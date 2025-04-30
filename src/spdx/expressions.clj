;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.expressions
  "SPDX license expression functionality, as defined in [SPDX v3.0.1 Annex . This functionality is bespoke (it does
  not use the parser in `Spdx-Java-Library`)."
  (:require [clojure.string         :as s]
            [instaparse.core        :as insta]
            [wreck.api              :as re]
            [spdx.licenses          :as lic]
            [spdx.exceptions        :as exc]
            [spdx.impl.replacements :as sir]))

(def ^:private case-sensitive-operators-fragment
  "<and>                  = <ws 'AND' ws>
   <or>                   = <ws 'OR' ws>
   <with>                 = <ws 'WITH' ws>")

(def ^:private case-insensitive-operators-fragment
  "<and>                  = <ws #\"(?i)AND\" ws>
   <or>                   = <ws #\"(?i)OR\" ws>
   <with>                 = <ws #\"(?i)WITH\" ws>")

; Adapted from ABNF grammar at https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/
(def ^:private spdx-license-expression-grammar-format "
  (* Simple terminals *)
  <ws>                   = <#\"\\s+\">
  <ows>                  = <#\"\\s*\">
  <id-string>            = #\"[\\p{Alnum}-\\.]+\"
  %s
  <or-later>             = <'+'>

  (* Identifiers *)
  license-id             = %s
  license-exception-id   = %s
  license-ref            = [<'DocumentRef-'> id-string <':'>] <'LicenseRef-'> id-string
  addition-ref           = [<'DocumentRef-'> id-string <':'>] <'AdditionRef-'> id-string

  (* 'License component' (hashmap) production rules *)
  license-or-later       = license-id or-later
  <license-component>    = license-id | license-or-later | license-ref
  <exception-component>  = license-exception-id | addition-ref
  with-expression        = license-component with exception-component

  (* Composite expression (vector) production rules *)
  <expression-component> = license-component | with-expression | <'('> expression <')'>
  and-expression         = expression-component (and expression-component)*
  or-expression          = and-expression (or and-expression)*
  expression             = ows or-expression ows")

(def ^:private license-ids-fragment   (delay (s/join " | " (map #(str "#\"(?i)" (re/esc %) "\"") (filter #(not (s/ends-with? % "+")) (lic/ids))))))  ; Filter out the few deprecated GNU ids that end in "+", since that's better handled by the grammar
(def ^:private exception-ids-fragment (delay (s/join " | " (map #(str "#\"(?i)" (re/esc %) "\"") (exc/ids)))))

(def ^:private spdx-license-expression-cs-grammar-d (delay (format spdx-license-expression-grammar-format
                                                                   case-sensitive-operators-fragment
                                                                   @license-ids-fragment
                                                                   @exception-ids-fragment)))
(def ^:private spdx-license-expression-ci-grammar-d (delay (format spdx-license-expression-grammar-format
                                                                   case-insensitive-operators-fragment
                                                                   @license-ids-fragment
                                                                   @exception-ids-fragment)))

(def ^:private spdx-license-expression-cs-parser-d (delay (insta/parser @spdx-license-expression-cs-grammar-d :start :expression)))
(def ^:private spdx-license-expression-ci-parser-d (delay (insta/parser @spdx-license-expression-ci-grammar-d :start :expression)))

(defn- walk-internal
  "Internal implementation of [[walk]]."
  [depth
   {:keys [op-fn license-fn group-fn]
      :or {op-fn      identity
           license-fn identity
           group-fn   (fn [_ group] group)}
      :as fns}
   parse-tree]
  (when parse-tree
    (cond
      (keyword?    parse-tree) (op-fn parse-tree)
      (map?        parse-tree) (license-fn parse-tree)
      (sequential? parse-tree) (let [group (some-> (seq (map (partial walk-internal (inc depth) fns) parse-tree)) vec)]   ; Note: naive (stack consuming) recursion - SPDX expression are rarely very deep
                                 (group-fn depth group)))))

#_{:clj-kondo/ignore [:unused-binding]}
(defn walk
  "Depth-first walk of `parse-tree` (i.e. obtained from [[parse]]), calling the
  associated functions (or [`clojure.core/identity`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/identity)
  when not provided) for each element in it.  Returns `nil` if `parse-tree` is
  `nil`.  Results are undefined for invalid parse trees.

  Keys in the `fns` map are:

  * `:op-fn`      - function of 1 argument (a keyword) to be called call when an
                    operator (`:and`, `:or`) is visited
  * `:license-fn` - function of 1 argument (a map) to be called when a license
                    map is visited
  * `:group-fn`   - function of **2** arguments (an integer and a sequence) to
                    be called when a group is visited. The first argument is the
                    current nesting depth of the walk (starting at 0 for the
                    outermost level), the second is the value of the group after
                    its elements have been walked"
  [{:keys [op-fn license-fn group-fn]
      :or {op-fn      identity
           license-fn identity
           group-fn   (fn [depth group] group)}
      :as fns}
   parse-tree]
  (when parse-tree
    (walk-internal 0 fns parse-tree)))

(defn- license-map->string
  "Turns a license map into a string. Returns `nil` if `m` is empty."
  [m]
  (when-not (empty? m)
    (str (when (:license-id m)           (:license-id m))
         (when (:or-later? m)            "+")
         (when (:license-ref m)          (lic/license-ref-map->string m))
         (when (:license-exception-id m) (str " WITH " (:license-exception-id m)))
         (when (:addition-ref m)         (str " WITH " (exc/addition-ref-map->string m))))))

(defn unparse
  "Turns a valid `parse-tree` (i.e. obtained from [[parse]]) back into an
  SPDX expression (a `String`), or `nil` if `parse-tree` is `nil`.  Results
  are undefined for invalid parse trees."
  [parse-tree]
  (some-> (walk {:op-fn      #(s/upper-case (name %))
                 :license-fn license-map->string
                 :group-fn   #(when (pos? (count %2))
                                (str (when (pos? %1) "(")
                                     (s/join (str " " (first %2) " ") (rest %2))
                                     (when (pos? %1) ")")))}
                parse-tree)
          s/trim))

(defn- canonicalise-nested-operators
  "Canonicalises nested operators that are the same."
  [operator coll]
  (loop [result [operator]
         f      (first coll)
         r      (rest coll)]
    (if-not f
      (vec result)
      (if (and (sequential? f)
               (= operator (first f)))
        (recur (concat result (rest f)) (first r) (rest r))
        (recur (concat result [f])      (first r) (rest r))))))

(defn- mandatory-license-id-replacements
  "Performs mandatory license id replacements on the parse tree (i.e. nonsensical GNU
  family ids that also contain +)."
  [parse-tree]
  (walk {:license-fn (fn [{:keys [license-id or-later?] :as m}]
                       (if-let [replacement (sir/replacement-for-license-id license-id or-later?)]
                         (let [result (assoc m :license-id (:license-id replacement))]
                           (if (:or-later? replacement)
                             (assoc result  :or-later? true)
                             (dissoc result :or-later?)))
                         m))}
        parse-tree))

(defn- replace-license-id-in-license-map
  "Replaces a deprecated :license-id entry in a license map if it has a
  replacement. Note that this replacement may result in a new nested `:and`
  clause, since license id replacements aren't always 1:1."
  [{:keys [license-id license-exception-id or-later?] :as m}]
  (if-let [license-replacement (sir/replacement-for-deprecated-license-id license-id or-later?)]
    (let [replacement-license-ids   (:license-ids license-replacement)
          replacement-or-later?     (:or-later?   license-replacement)
          replacement-exception-id  (:license-exception-id license-replacement)
          replacement-exception-ids (seq (filter identity [license-exception-id replacement-exception-id]))
          result                    (mapcat #(if replacement-exception-ids
                                               (map (fn [replacement-exception-id]
                                                      (merge {:license-id % :license-exception-id replacement-exception-id}
                                                             (when replacement-or-later? {:or-later? true})))
                                                    replacement-exception-ids)
                                               [(merge {:license-id %} (when replacement-or-later? {:or-later? true}))])
                                            replacement-license-ids)]
          (case (count result)
            0 nil   ; This should never happen, but just in case we return nil so we don't fall through to the default case and get weird results...
            1 (first result)
            (vec (concat [:and] result))))
    m))

(defn- replace-deprecated-entries-in-license-map
  "Replaces any deprecated entries in a license map that have replacements."
  [{:keys [license-id license-exception-id] :as m}]
  ; We perform license exception id replacement first, as it's a simple 1:1
  (let [replacement-exception-id (or (sir/replacement-for-deprecated-exception-id license-exception-id) license-exception-id)
        result                   (merge m (when replacement-exception-id {:license-exception-id replacement-exception-id}))]
    (if license-id
      ; It's a listed license, so perform license id replacement too
      (replace-license-id-in-license-map result)
      ; It's a LicenseRef, so skip license id replacement
      result)))

(defn- canonicalise-deprecated-ids
  "Canonicalises deprecated SPDX identifiers, based on the replacement rules
  provided by [[spdx.impl.replacements]]."
  [parse-tree]
  (walk {:license-fn replace-deprecated-entries-in-license-map
         :group-fn   (fn [_ [operator & entries]] (canonicalise-nested-operators operator entries))}
        parse-tree))

(defn- license-map->sortable-string
  "Turns a license map into a string suitable for sorting (but NOT suitable for
  display or any other purpose). Returns `nil` if `m` is empty."
  [m]
  (when-not (empty? m)
    (str (when (:license-id m)           (s/lower-case (:license-id m)))
         (when (:or-later? m)            "+")
         (when (:license-ref m)          (lic/license-ref-map->string m))
         (when (:license-exception-id m) (s/lower-case (str " " (:license-exception-id m))))
         (when (:addition-ref m)         (str " " (exc/addition-ref-map->string m))))))

(defn- compare-license-maps
  "Compares two license maps, as found in a parse tree."
  [x y]
  (cond
    ; license-ids first
    (and (:license-id x) (:license-id y))   (if (and (= (s/lower-case (:license-id x)) (s/lower-case (:license-id y)))
                                                     (= (:or-later? x) (:or-later? y)))
                                              (cond
                                                ; exception-ids first
                                                (and (:license-exception-id x) (:addition-ref         y)) -1
                                                ; then AdditionRefs
                                                (and (:addition-ref         x) (:license-exception-id y)) 1
                                                :else                          (compare (license-map->sortable-string x) (license-map->sortable-string y)))
                                              (compare (license-map->sortable-string x) (license-map->sortable-string y)))
    (:license-id x)                         -1
    (:license-id y)                         1
    ; then LicenseRefs
    (and (:license-ref x) (:license-ref y)) (if (and (= (:license-ref x)  (:license-ref y))
                                                     (= (:document-ref x) (:document-ref y)))
                                              (cond
                                                ; exception-ids first
                                                (and (:license-exception-id x) (:addition-ref         y)) -1
                                                ; then AdditionRefs
                                                (and (:addition-ref         x) (:license-exception-id y)) 1
                                                :else                          (compare (license-map->sortable-string x) (license-map->sortable-string y)))
                                              (compare (license-map->sortable-string x) (license-map->sortable-string y)))
    (:license-ref x)                        -1
    (:license-ref y)                        1
    :else                                   1))

(defn- compare-license-sequences
  "Compares two license sequences, as found in a parse tree.  Comparisons are
  based on length - first by number of elements, then, for equi-sized sequences,
  by lexicographical length (which is a little hokey, but ensures that 'longest'
  sequences go last, for a reasonable definition of 'longest')."
  [x y]
  (let [result (compare (count x) (count y))]
    (if (= 0 result)
      (compare (unparse x) (unparse y))
      result)))

(defn- parse-tree-compare
  "sort-by comparator for parse-trees"
  [x y]
  (cond
    ; Keywords (operators) always come first
    (and (keyword? x) (keyword? y))       (compare x y)
    (keyword? x)                          -1
    (keyword? y)                          1
    ; Then maps (licenses)
    (and (map? x) (map? y))               (compare-license-maps x y)       ; Because compare doesn't support maps
    (map? x)                              -1
    ; And sequences (sub-clauses) last
    (and (sequential? x) (sequential? y)) (compare-license-sequences x y)  ; Because compare doesn't support maps (which will be elements inside x and y)
    :else                                 1))

(defn- sort-parse-tree
  "Sorts the parse tree so that logically equivalent expressions produce the
  same parse tree e.g. parsing `Apache-2.0 OR MIT` will produce the same parse
  tree as parsing `MIT OR Apache-2.0`."
  [parse-tree]
  (walk {:group-fn #(some-> (seq (sort-by identity parse-tree-compare %2)) vec)}
        parse-tree))

(defn- collapse-redundant-clauses
  "Collapses redundant clauses in `parse-tree`."
  [parse-tree]
  (walk {:group-fn #(let [result (vec (distinct %2))]
                      (if (= 2 (count result))
                        (second result)
                        result))}
        parse-tree))

(defn parse-with-info
  "As for [[parse]], but returns an [instaparse parse error](https://github.com/Engelberg/instaparse#parse-errors)
  if parsing fails, instead of `nil`.

  `opts` are as for [[parse]]"
  ([s] (parse-with-info s nil))
  ([^String s {:keys [canonicalise-deprecated-ids?
                      case-sensitive-operators?
                      collapse-redundant-clauses?
                      sort-licenses?]
                 :or {canonicalise-deprecated-ids? true
                      case-sensitive-operators?    false
                      collapse-redundant-clauses?  true
                      sort-licenses?               true}}]
   (when-not (s/blank? s)
     (let [parser     (if case-sensitive-operators? @spdx-license-expression-cs-parser-d @spdx-license-expression-ci-parser-d)
           parse-tree (insta/parse parser s)]
       (if (insta/failure? parse-tree)
         parse-tree
         (as-> parse-tree parse-tree
               (insta/transform {:license-id           #(hash-map  :license-id           (lic/canonicalise-id (first %&)))
                                 :license-exception-id #(hash-map  :license-exception-id (exc/canonicalise-id (first %&)))
                                 :license-ref          #(case (count %&)
                                                          1 {:license-ref  (first %&)}
                                                          2 {:document-ref (first %&) :license-ref (second %&)})
                                 :addition-ref         #(case (count %&)
                                                          1 {:addition-ref  (first %&)}
                                                          2 {:addition-document-ref (first %&) :addition-ref (second %&)})
                                 :license-or-later     #(merge {:or-later? true} (first %&))
                                 :with-expression      #(merge (first %&)        (second %&))
                                 :and-expression       #(case (count %&)
                                                          1 (first %&)
                                                          (canonicalise-nested-operators :and %&))
                                 :or-expression        #(case (count %&)
                                                          1 (first %&)
                                                          (canonicalise-nested-operators :or %&))
                                 :expression           #(case (count %&)
                                                          1 (first %&)
                                                          (vec %&))}
                                parse-tree)
               (mandatory-license-id-replacements parse-tree)
               (if canonicalise-deprecated-ids? (canonicalise-deprecated-ids parse-tree) parse-tree)
               (if sort-licenses?               (sort-parse-tree             parse-tree) parse-tree)
               (if collapse-redundant-clauses?  (collapse-redundant-clauses  parse-tree) parse-tree)
               (if (and collapse-redundant-clauses?
                        sort-licenses?)         (sort-parse-tree             parse-tree) parse-tree)))))))  ; Post-sort, to ensure results of collapsing redundant clauses get sorted

#_{:clj-kondo/ignore [:unused-binding]}
(defn parse
  "Attempt to parse `s` (a `String`) as an [SPDX license expression](https://spdx.github.io/spdx-spec/v3.0/annexes/SPDX-license-expressions/),
  returning a data structure representing the parse tree, or `nil` if it cannot
  be parsed.  Licenses and associated license exceptions / 'or later' markers
  (if any) are represented as a map, groups of licenses separated by operators
  are represented as vectors with the operator represented by a keyword in the
  first element in the vector and with license maps in the rest of the vector.
  Groups (vectors) may be nested e.g. when the expression contains nested
  clauses.

  The optional `opts` map has these keys:

  * `:canonicalise-deprecated-ids?` (`boolean`, default `true`) - controls
    whether deprecated ids in the expression are canonicalised to their
    non-deprecated equivalents (where possible) as part of the parsing process.
    Note that not all deprecated identifiers have non-deprecated equivalents,
    and those will be left unchanged in the parse tree.
  * `:case-sensitive-operators?` (`boolean`, default `false`) - controls whether
    operators in expressions (`AND`, `OR`, `WITH`) are case-sensitive
    (spec-compliant, but strict) or not (non-spec-compliant, lenient).
  * `:collapse-redundant-clauses?` (`boolean`, default `true`) - controls
    whether redundant clauses (e.g. `\"Apache-2.0 AND Apache-2.0\"`) are
    collapsed during parsing.  Note: disabling sorting (`:sort-licenses?`) may
    cause redundant clauses to remain in the parse tree.
  * `:sort-licenses?` (`boolean`, default `true`) - controls whether licenses
    that appear at the same level in the parse tree are sorted alphabetically.
    This means that some parse trees will be identical for different (though
    logically identical) inputs, which can be useful in many cases.  For example
    the parse tree for `Apache-2.0 OR MIT` would be identical to the parse tree
    for `MIT OR Apache-2.0`.

  Deprecated & removed `opts`:

  * `:normalise-deprecated-ids?` - superceded by `:canonicalise-deprecated-ids?`
  * `:normalise-gpl-ids?` - superceded by `:canonicalise-deprecated-ids?`

  Notes:

  * The parser always canonicalises SPDX identifiers
    e.g. `aPAcHe-2.0` -> `Apache-2.0`
  * The parser always removes redundant grouping
    e.g. `(((((Apache-2.0))))))` -> `Apache-2.0`
  * The parser always corrects nonsensical combinations of GNU family
    license identifiers with the 'or later' marker
    e.g. `GPL-3.0-only+` -> `GPL-3.0-or-later`
  * The parser synthesises grouping when needed to make SPDX license
    expressions' precedence rules explicit (see [the relevant section within
    annex D of the SPDX specification](https://spdx.github.io/spdx-spec/v3.0/annexes/SPDX-license-expressions/#d45-order-of-precedence-and-parentheses)
    for details).
  * The default `opts` result in parsing that is more lenient than the SPDX
    specification and is therefore not strictly spec compliant.  You can enable
    strictly spec compliant parsing by setting `case-sensitive-operators?` to
    `true`.

  Examples (assuming default options):

  ```clojure
  ; Simple SPDX expression (single license identifier)
  (parse \"Apache-2.0\")
  {:license-id \"Apache-2.0\"}

  ; Identifier case correction, or-later? flag
  (parse \"apache-2.0+\")
  {:license-id \"Apache-2.0\" :or-later? true}

  ; GNU family identifier canonicalisation
  (parse \"GPL-2.0+\")
  {:license-id \"GPL-2.0-or-later\"}

  ; Deprecated identifier canonicalisation
  (parse \"StandardML-NJ\")
  {:license-id \"SMLNJ\"}

  ; License exceptions
  (parse \"GPL-2.0 WITH Classpath-exception-2.0\")
  {:license-id \"GPL-2.0-only\"
   :license-exception-id \"Classpath-exception-2.0\"}

  ; Nesting (due to operator precedence), and sorting
  (parse \"MIT OR BSD-2-Clause AND Apache-2.0\")
  [:or
   {:license-id \"MIT\"}
   [:and
    {:license-id \"Apache-2.0\"}
    {:license-id \"BSD-2-Clause\"}]]

  ; Case insensitive operators
  (parse \"(GPL-2.0+ with Classpath-exception-2.0) or CDDL-1.1\")
  [:or
   {:license-id \"CDDL-1.1\"}
   {:license-id \"GPL-2.0-or-later\"
    :license-exception-id \"Classpath-exception-2.0\"}]

  ; LicenseRefs (custom license identifiers)
  (parse \"DocumentRef-foo:LicenseRef-bar\")
  {:document-ref \"foo\"
   :license-ref \"bar\"}

  ; AdditionRefs (custom license exception identifiers, added in SPDX 3.0)
  (parse \"Apache-2.0 with DocumentRef-foo:AdditionRef-bar\")
  {:license-id \"Apache-2.0\"
   :addition-document-ref \"foo\"
   :addition-ref \"bar\"}
  ```"
  ([s] (parse s nil))
  ([s {:keys [canonicalise-deprecated-ids?
              case-sensitive-operators?
              collapse-redundant-clauses?
              sort-licenses?]
         :or {canonicalise-deprecated-ids? true
              case-sensitive-operators?    false
              collapse-redundant-clauses?  true
              sort-licenses?               true}
         :as opts}]
   (when-let [raw-parse-tree (parse-with-info s opts)]
     (when-not (insta/failure? raw-parse-tree)
       raw-parse-tree))))

(defn canonicalise
  "Canonicalises an SPDX expression, by running it through [[parse]] then
  [[unparse]].  Returns `nil` if `s` is not a valid SPDX expression.

  `opts` are as for [[parse]]"
  ([^String s] (canonicalise s nil))
  ([^String s opts]
   (some-> s
           (parse opts)
           unparse)))

(defn ^:deprecated normalise
  "Deprecated - use [[canonicalise]] instead."
  ([^String s]      (canonicalise s nil))
  ([^String s opts] (canonicalise s opts)))

(defn valid?
  "Is `s` (a `String`) a valid SPDX license expression?

  Note: if you intend to parse `s` if it's valid, it's more efficient to call
  [[parse]] directly and check for a `nil` result instead of calling this
  function first (doing so avoids double parsing).

  The optional `opts` map has these keys:

  * `:case-sensitive-operators?` (`boolean`, default `false`) - controls whether
    operators in expressions (`AND`, `OR`, `WITH`) are case-sensitive
    (spec-compliant, but strict) or not (non-spec-compliant, lenient)."
  ([^String s] (valid? s nil))
  ([^String s {:keys [case-sensitive-operators?]
                 :or {case-sensitive-operators? false}}]
   (let [parser (if case-sensitive-operators? @spdx-license-expression-cs-parser-d @spdx-license-expression-ci-parser-d)]
     (not (or (s/blank? s)
              (insta/failure? (insta/parse parser s)))))))

(defn simple?
  "Is `s` (a `String`) a 'simple' SPDX license expression (i.e. one that
  contains no AND or OR operators, though it may contain a WITH operator)?
  Returns `nil` if `s` not a valid SPDX expression.

  The optional `opts` map is as for `parse`."
  ([^String s] (simple? s nil))
  ([^String s opts]
   (when-let [p (parse s opts)]
     (map? p))))

; Note: we can't use complement here, due to the presence of nil results from simple?
(defn compound?
  "Is `s` (a `String`) a 'compound' SPDX license expression (i.e. one that
  contains at least one AND or OR operator)?  Returns `nil` if `s` not a valid
  SPDX expression.

  The optional `opts` map is as for `parse`."
  ([^String s] (compound? s nil))
  ([^String s opts]
    (let [result (simple? s opts)]
      (when-not (nil? result)
        (not result)))))

(defn extract-ids
  "Extract all SPDX ids (as a set of `String`s) from `parse-tree`.  Results are
  undefined for invalid parse trees.

  The optional `opts` map has these keys:

  * `:include-or-later?` (`boolean`, default `false`) - controls whether the output
    includes the 'or later' indicator (`+`) after license ids that have that
    designation in the parse tree."
  ([parse-tree] (extract-ids parse-tree nil))
  ([parse-tree  {:keys [include-or-later?] :or {include-or-later? false}}]
   (walk {:license-fn #(into #{} (filter identity [(when (:license-id           %) (str (:license-id %) (when (and include-or-later? (:or-later? %)) "+")))
                                                   (when (:license-exception-id %) (:license-exception-id        %))
                                                   (when (:license-ref          %) (lic/license-ref-map->string  %))
                                                   (when (:addition-ref         %) (exc/addition-ref-map->string %))]))
          :group-fn   #(not-empty (into #{} cat (rest %2)))}  ; Strip leading operator keyword then flatten the rest (%2 is a 2-level nested sequence) and put in a set
         parse-tree)))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this function may have a substantial performance cost."
  []
  (lic/init!)
  (exc/init!)
  @license-ids-fragment
  @exception-ids-fragment
; Note: we always leave these to runtime, since they're not expensive, and doing so
; ensures that callers who exclusively use one parsing variant aren't paying an
; unnecessary memory cost.
;  @spdx-license-expression-ci-grammar-d
;  @spdx-license-expression-cs-grammar-d
;  @spdx-license-expression-ci-parser-d
;  @spdx-license-expression-cs-parser-d
  nil)
