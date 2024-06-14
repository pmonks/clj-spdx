;
; Copyright © 2023 Peter Monks
;
; Licensed under the Apache License Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;
; SPDX-License-Identifier: Apache-2.0
;

(ns spdx.expressions
  "SPDX license expression functionality. This functionality is bespoke (it does
  not use the parser in `Spdx-Java-Library`)."
  (:require [clojure.string  :as s]
            [clojure.set     :as set]
            [instaparse.core :as insta]
            [spdx.licenses   :as lic]
            [spdx.exceptions :as exc]))

(def ^:private case-sensitive-operators-fragment
  "<and>                  = <ws 'AND' ws>
   <or>                   = <ws 'OR' ws>
   <with>                 = <ws 'WITH' ws>")

(def ^:private case-insensitive-operators-fragment
  "<and>                  = <ws #\"(?i)AND\" ws>
   <or>                   = <ws #\"(?i)OR\" ws>
   <with>                 = <ws #\"(?i)WITH\" ws>")

; Adapted from ABNF grammar at https://spdx.github.io/spdx-spec/v3.0/annexes/SPDX-license-expressions/
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

(defn- escape-re
  "Escapes the given string for use in a regex."
  [s]
  (when s
    (s/escape s {\< "\\<"
                 \( "\\("
                 \[ "\\["
                 \{ "\\{"
                 \\ "\\\\"
                 \^ "\\^"
                 \- "\\-"
                 \= "\\="
                 \$ "\\$"
                 \! "\\!"
                 \| "\\|"
                 \] "\\]"
                 \} "\\}"
                 \) "\\)"
                 \? "\\?"
                 \* "\\*"
                 \+ "\\+"
                 \. "\\."
                 \> "\\>"
                 })))

(def ^:private license-ids-fragment   (delay (s/join " | " (map #(str "#\"(?i)" (escape-re %) "\"") (filter #(not (s/ends-with? % "+")) (lic/ids))))))  ; Filter out the few deprecated ids that end in "+", since they break the parser)
(def ^:private exception-ids-fragment (delay (s/join " | " (map #(str "#\"(?i)" (escape-re %) "\"") (exc/ids)))))

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

(def ^:private normalised-spdx-ids-map-d (delay (merge (into {} (map #(vec [(s/lower-case %) %]) (lic/ids)))
                                                       (into {} (map #(vec [(s/lower-case %) %]) (exc/ids))))))

(def ^:private current-gpl-family-ids #{
                                "AGPL-1.0-only" "AGPL-1.0-or-later" "AGPL-3.0-only" "AGPL-3.0-or-later"
                                "GPL-1.0-only" "GPL-1.0-or-later" "GPL-2.0-only" "GPL-2.0-or-later" "GPL-3.0-only" "GPL-3.0-or-later"
                                "LGPL-2.0-only" "LGPL-2.0-or-later" "LGPL-2.1-only" "LGPL-2.1-or-later" "LGPL-3.0-only" "LGPL-3.0-or-later"})

(def ^:private deprecated-simple-gpl-family-ids {
                                "AGPL-1.0"  "AGPL-1.0-only"
                                ; Note: AGPL-1.0+ never existed as a listed SPDX license identifier
                                "AGPL-3.0"  "AGPL-3.0-only"
                                ; Note: AGPL-3.0+ never existed as a listed SPDX license identifier
                                "GPL-1.0"   "GPL-1.0-only"
                                "GPL-1.0+"  "GPL-1.0-or-later"
                                "GPL-2.0"   "GPL-2.0-only"
                                "GPL-2.0+"  "GPL-2.0-or-later"
                                "GPL-3.0"   "GPL-3.0-only"
                                "GPL-3.0+"  "GPL-3.0-or-later"
                                "LGPL-2.0"  "LGPL-2.0-only"
                                "LGPL-2.0+" "LGPL-2.0-or-later"
                                "LGPL-2.1"  "LGPL-2.1-only"
                                "LGPL-2.1+" "LGPL-2.1-or-later"
                                "LGPL-3.0"  "LGPL-3.0-only"
                                "LGPL-3.0+" "LGPL-3.0-or-later"})

(def ^:private deprecated-compound-gpl-family-ids {
                                "GPL-2.0-with-autoconf-exception"  ["GPL-2.0-only" "Autoconf-exception-2.0"]
                                "GPL-2.0-with-bison-exception"     ["GPL-2.0-only" "Bison-exception-2.2"]
                                "GPL-2.0-with-classpath-exception" ["GPL-2.0-only" "Classpath-exception-2.0"]
                                "GPL-2.0-with-font-exception"      ["GPL-2.0-only" "Font-exception-2.0"]
                                "GPL-2.0-with-GCC-exception"       ["GPL-2.0-only" "GCC-exception-2.0"]
                                "GPL-3.0-with-autoconf-exception"  ["GPL-3.0-only" "Autoconf-exception-3.0"]
                                "GPL-3.0-with-GCC-exception"       ["GPL-3.0-only" "GCC-exception-3.1"]})

(def ^:private deprecated-gpl-family-ids (set/union (set (keys deprecated-simple-gpl-family-ids)) (set (keys deprecated-compound-gpl-family-ids))))
(def ^:private gpl-family-ids            (set/union current-gpl-family-ids deprecated-gpl-family-ids))

(def ^:private not-blank? (complement s/blank?))

(defn- license-ref->string
  "Turns map `m` containing a license-ref into a String, returning `nil` if
  there isn't one."
  [m]
  (when (and m (:license-ref m))
    (str (when-let [document-ref (:document-ref m)] (str "DocumentRef-" document-ref ":"))
         "LicenseRef-" (:license-ref m))))

(defn- addition-ref->string
  "Turns map `m` containing an addition-ref into a String, returning `nil` if
  there isn't one."
  [m]
  (when (and m (:addition-ref m))
    (str (when-let [addition-document-ref (:addition-document-ref m)] (str "DocumentRef-" addition-document-ref ":"))
         "AdditionRef-" (:addition-ref m))))

(defn- license-map->string
  "Turns a license map into a (non-readable) string, primarily for the purposes
  of comparison."
  [m]
  (when m
    (str (when (:license-id m)           (:license-id m))
         (when (:or-later? m)            "+")
         (when (:license-ref m)          (license-ref->string m))
         (when (:license-exception-id m) (str " WITH " (:license-exception-id m)))
         (when (:addition-ref m)         (str " WITH " (addition-ref->string m))))))

(defn- unparse-internal
  "Internal implementation of unparse."
  [level parse-result]
  (when parse-result
    (cond
      (sequential? parse-result)
        (when (pos? (count parse-result))
          (let [op-str (str " " (s/upper-case (name (first parse-result))) " ")]
            (str (when (pos? level) "(")
                 (s/join op-str (map (partial unparse-internal (inc level)) (rest parse-result)))  ; Note: naive (stack consuming) recursion
                 (when (pos? level) ")"))))
      (map? parse-result)
        (license-map->string parse-result))))

(defn unparse
  "Turns a valid `parse-result` (i.e. obtained from [[parse]]) back into an
  SPDX expression (a `String`), or `nil` if `parse-result` is `nil`.  Results
  are undefined for invalid parse trees."
  [parse-result]
  (when-let [result (unparse-internal 0 parse-result)]
    (when-not (s/blank? result)
      (s/trim result))))

(defn- normalise-nested-operators
  "Normalises nested operators of the same type."
  [type coll]
  (loop [result [type]
         f      (first coll)
         r      (rest coll)]
    (if-not f
      (vec result)
      (if (and (sequential? f)
               (= type (first f)))
        (recur (concat result (rest f)) (first r) (rest r))
        (recur (concat result [f])      (first r) (rest r))))))

(defn- normalise-gpl-id
  "Normalises a GPL family `license-id` to a tuple (2 element vector) containing
  the non-deprecated equivalent license id in first position, and (optionally -
  may be nil) a license-exception-id in second position if `license-id` was a
  compound id (an id that also identifies an exception, such as
  \"GPL-2.0-with-classpath-exception\").

  If `license-id` is not deprecated, returns it as-is (in the first position in
  the tuple)."
  [license-id]
  (get deprecated-compound-gpl-family-ids
       license-id
       [(get deprecated-simple-gpl-family-ids
             license-id
             license-id)
        nil]))

(defn- normalise-gpl-license-map
  "Normalises a license map that is known to contain a GPL family `license-id`.
  This involves:
  1. Replacing deprecated GPL family license ids with their non-deprecated
     equivalent
  2. Turning `:or-later?` flags into the '-or-later' variant of the `license-id`
  3. Expanding 'compound' license ids (e.g. GPL-2.0-with-classpath-exception)"
  [{:keys [license-id or-later? license-exception-id]}]
  (let [[new-license-id new-license-exception-id] (normalise-gpl-id license-id)
        new-license-id                            (let [or-later-variant (s/replace new-license-id "-only" "-or-later")]
                                                    (if (and or-later? (lic/listed-id? or-later-variant))
                                                      or-later-variant
                                                      new-license-id))]
    ; Check if we have two license exception ids after expanding the license-id (e.g. from a valid but weird expression such as "GPL-2.0-with-autoconf-exception WITH Classpath-exception-2.0")
    (if (and (not-blank? license-exception-id)
             (not-blank? new-license-exception-id)
             (not= license-exception-id new-license-exception-id))
      [:and {:license-id new-license-id :license-exception-id new-license-exception-id}
            {:license-id new-license-id :license-exception-id license-exception-id}]
      (merge {:license-id new-license-id}
             (when (not-blank? license-exception-id)     {:license-exception-id license-exception-id})
             (when (not-blank? new-license-exception-id) {:license-exception-id new-license-exception-id})))))

(defn- normalise-gpl-elements
  "Normalises all of the GPL elements in `parse-tree`."
  [parse-tree]
  (cond
    (keyword?    parse-tree) parse-tree
    (map?        parse-tree) (if (contains? gpl-family-ids (:license-id parse-tree))
                               (normalise-gpl-license-map parse-tree)
                               parse-tree)
    (sequential? parse-tree) (some-> (seq (map normalise-gpl-elements parse-tree)) vec)))  ; Note: naive (stack consuming) recursion

(defn- collapse-redundant-clauses
  "Collapses redundant clauses in `parse-tree`."
  [parse-tree]
  (cond
    (keyword?    parse-tree) parse-tree
    (map?        parse-tree) parse-tree
    (sequential? parse-tree) (let [result (some-> (seq (distinct (map collapse-redundant-clauses parse-tree))) vec)]  ; Note: naive (stack consuming) recursion
                               (if (= 2 (count result))
                                 (second result)
                                 result))))

(defn- compare-license-maps
  "Compares two license maps, as found in a parse tree."
  [x y]
  ; Todo: consider case-insensitive sorting in future, assuming LicenseRefs & AdditionRefs are _not_ case sensitive (awaiting feedback from spdx-tech on that...)
  (compare (license-map->string x) (license-map->string y)))

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
    (and (keyword? x) (keyword? y))       (compare x y)
    (keyword? x)                          -1
    (keyword? y)                          1
    (and (map? x) (map? y))               (compare-license-maps x y)       ; Because compare doesn't support maps
    (map? x)                              -1
    (and (sequential? x) (sequential? y)) (compare-license-sequences x y)  ; Because compare doesn't support maps (which will be elements inside x and y)
    :else                                 1))

(defn- sort-parse-tree
  "Sorts the parse tree so that logically equivalent expressions produce the
  same parse tree e.g. parsing `Apache-2.0 OR MIT` will produce the same parse
  tree as parsing `MIT OR Apache-2.0`."
  [parse-tree]
  (cond
    (keyword?    parse-tree) parse-tree
    (map?        parse-tree) parse-tree
    (sequential? parse-tree) (let [result (some-> (seq (map sort-parse-tree parse-tree)) vec)]  ; Note: naive (stack consuming) recursion
                               (some-> (seq (sort-by identity parse-tree-compare result)) vec))))

(defn parse-with-info
  "As for [[parse]], but returns an [instaparse parse error](https://github.com/Engelberg/instaparse#parse-errors)
  if parsing fails, instead of `nil`.

  `opts` are as for [[parse]]"
  ([s] (parse-with-info s nil))
  ([^String s {:keys [normalise-gpl-ids?
                      case-sensitive-operators?
                      collapse-redundant-clauses?
                      sort-licenses?]
                 :or {normalise-gpl-ids?          true
                      case-sensitive-operators?   false
                      collapse-redundant-clauses? true
                      sort-licenses?              true}}]
   (when-not (s/blank? s)
     (let [parser (if case-sensitive-operators? @spdx-license-expression-cs-parser-d @spdx-license-expression-ci-parser-d)
           result (insta/parse parser s)]
       (if (insta/failure? result)
         result
         (let [result (insta/transform {:license-id            #(hash-map  :license-id           (get @normalised-spdx-ids-map-d (s/lower-case (first %&)) (first %&)))
                                        :license-exception-id  #(hash-map  :license-exception-id (get @normalised-spdx-ids-map-d (s/lower-case (first %&)) (first %&)))
                                        :license-ref           #(case (count %&)
                                                                  1 {:license-ref  (first %&)}
                                                                  2 {:document-ref (first %&) :license-ref (second %&)})
                                        :addition-ref          #(case (count %&)
                                                                  1 {:addition-ref  (first %&)}
                                                                  2 {:addition-document-ref (first %&) :addition-ref (second %&)})
                                        :license-or-later      #(merge {:or-later? true} (first %&))
                                        :with-expression       #(merge (first %&)        (second %&))
                                        :and-expression        #(case (count %&)
                                                                  1 (first %&)
                                                                  (normalise-nested-operators :and %&))
                                        :or-expression         #(case (count %&)
                                                                  1 (first %&)
                                                                  (normalise-nested-operators :or %&))
                                        :expression            #(case (count %&)
                                                                  1 (first %&)
                                                                  (vec %&))}
                                       result)
               result (if normalise-gpl-ids?          (normalise-gpl-elements result)     result)
               result (if collapse-redundant-clauses? (collapse-redundant-clauses result) result)
               result (if sort-licenses?              (sort-parse-tree result)            result)]
           result))))))

#_{:clj-kondo/ignore [:unused-binding]}
(defn parse
  "Attempt to parse `s` (a `String`) as an [SPDX license expression](https://spdx.github.io/spdx-spec/v3.0/annexes/SPDX-license-expressions/),
  returning a data structure representing the parse tree, or `nil` if it cannot
  be parsed.

  The optional `opts` map has these keys:

  * `:normalise-gpl-ids?` (`boolean`, default `true`) - controls whether
    deprecated 'historical oddity' GPL family ids in the expression are
    normalised to their non-deprecated replacements as part of the parsing
    process.
  * `:case-sensitive-operators?` (`boolean`, default `false`) - controls whether
    operators in expressions (`AND`, `OR`, `WITH`) are case-sensitive
    (spec-compliant, but strict) or not (non-spec-compliant, lenient).
  * `:collapse-redundant-clauses?` (`boolean`, default `true`) - controls
    whether redundant clauses (e.g. \"Apache-2.0 AND Apache-2.0\") are
    collapsed during parsing.
  * `:sort-licenses?` (`boolean`, default `true`) - controls whether licenses
    that appear at the same level in the parse tree are sorted alphabetically.
    This means that some parse trees will be identical for different (though
    logically identical) inputs, which can be useful in many cases.  For example
    the parse tree for `Apache-2.0 OR MIT` would be identical to the parse tree
    for `MIT OR Apache-2.0`.

  Notes:

  * The parser always normalises SPDX ids to their canonical case
    e.g. `aPAcHe-2.0` -> `Apache-2.0`
  * The parser always removes redundant grouping
    e.g. `(((((Apache-2.0))))))` -> `Apache-2.0`
  * The parser synthesises grouping when needed to make SPDX license
    expressions' precedence rules explicit (see [the relevant section within
    annex D of the SPDX specification](https://spdx.github.io/spdx-spec/v3.0/annexes/SPDX-license-expressions/#d45-order-of-precedence-and-parentheses)
    for details).
  * The default `opts` result in parsing that is more lenient than the SPDX
    specification and is therefore not strictly spec compliant.  You can enable
    strictly spec compliant parsing by setting `normalise-gpl-ids?` to `false`
    and `case-sensitive-operators?` to `true`.

  Examples (assuming default options):

  ```clojure
  (parse \"Apache-2.0\")
  {:license-id \"Apache-2.0\"}

  (parse \"apache-2.0+\")
  {:license-id \"Apache-2.0\" :or-later? true}  ; Note id case correction

  (parse \"GPL-2.0+\")
  {:license-id \"GPL-2.0-or-later\"}  ; Note GPL-family id normalisation

  (parse \"GPL-2.0 WITH Classpath-exception-2.0\")
  {:license-id \"GPL-2.0-only\"
   :license-exception-id \"Classpath-exception-2.0\"}

  (parse \"(GPL-2.0+ with Classpath-exception-2.0) or CDDL-1.1\")  ; Note sorting
  [:or
   {:license-id \"CDDL-1.1\"}
   {:license-id \"GPL-2.0-or-later\"
    :license-exception-id \"Classpath-exception-2.0\"}]

  (parse \"DocumentRef-foo:LicenseRef-bar\")
  {:document-ref \"foo\"
   :license-ref \"bar\"}

  (parse \"Apache-2.0 with DocumentRef-foo:AdditionRef-bar\")
  {:license-id \"Apache-2.0\"
   :addition-document-ref \"foo\"
   :addition-ref \"bar\"}
  ```"
  ([s] (parse s nil))
  ([s {:keys [normalise-gpl-ids?
              case-sensitive-operators?
              collapse-redundant-clauses?
              sort-licenses?]
         :or {normalise-gpl-ids?          true
              case-sensitive-operators?   false
              collapse-redundant-clauses? true
              sort-licenses?              true}
         :as opts}]
   (when-let [raw-parse-result (parse-with-info s opts)]
     (when-not (insta/failure? raw-parse-result)
       raw-parse-result))))

(defn normalise
  "Normalises an SPDX expression, by running it through [[parse]] then
  [[unparse]].  Returns `nil` if `s` is not a valid SPDX expression.

  `opts` are as for [[parse]]"
  ([s] (normalise s nil))
  ([s opts]
   (some-> s
           (parse opts)
           unparse)))

(defn valid?
  "Is `s` (a `String`) a valid SPDX license expression?

  Note: if you intend to parse `s` if it's valid, it's more efficient to call
  [[parse]] directly and check for a `nil` result instead of calling this method
  first (doing so avoids double parsing).

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
  "Extract all SPDX ids (as a set of `String`s) from `parse-result`.

  The optional `opts` map has these keys:

  * `:include-or-later?` (`boolean`, default `false`) - controls whether the output
    includes the 'or later' indicator (`+`) after license ids that have that
    designation in the parse tree."
  ([parse-result] (extract-ids parse-result nil))
  ([parse-result  {:keys [include-or-later?] :or {include-or-later? false} :as opts}]
   (when parse-result
     (cond
       (sequential? parse-result) (set (mapcat #(extract-ids % opts) parse-result))  ; Note: naive (stack consuming) recursion
       (map?        parse-result) (set/union (when (:license-id           parse-result) #{(str (:license-id parse-result) (when (and include-or-later? (:or-later? parse-result)) "+"))})
                                             (when (:license-exception-id parse-result) #{(:license-exception-id parse-result)})
                                             (when (:license-ref          parse-result) #{(license-ref->string parse-result)})
                                             (when (:addition-ref         parse-result) #{(addition-ref->string parse-result)}))
       :else        nil))))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this method may have a substantial performance cost."
  []
  (lic/init!)
  (exc/init!)
  @license-ids-fragment
  @exception-ids-fragment
  @normalised-spdx-ids-map-d
; Note: we always leave these to runtime, since they're not expensive, and doing so
; ensures that callers who exclusively use one parsing variant aren't paying an
; unnecessary memory cost.
;  @spdx-license-expression-ci-grammar-d
;  @spdx-license-expression-cs-grammar-d
;  @spdx-license-expression-ci-parser-d
;  @spdx-license-expression-cs-parser-d
  nil)
