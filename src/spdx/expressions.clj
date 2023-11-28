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
  "SPDX license expression functionality. This functionality is bespoke (it is
  not provided by Spdx-Java-Library)."
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

; Adapted from ABNF grammar at https://spdx.github.io/spdx-spec/v2.3/SPDX-license-expressions/
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

  (* 'License component' (hashmap) production rules *)
  license-or-later       = license-id or-later
  <license-component>    = license-id | license-or-later | license-ref
  with-expression        = license-component with license-exception-id

  (* Composite expression production rules *)
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
    (sequential? parse-tree) (some-> (seq (map normalise-gpl-elements parse-tree)) vec)  ; Note: naive (stack consuming) recursion
    (map?        parse-tree) (if (contains? gpl-family-ids (:license-id parse-tree))
                               (normalise-gpl-license-map parse-tree)
                               parse-tree)))

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

(defn parse-with-info
  "As for parse, but returns an instaparse parse error info if parsing fails,
  instead of nil. See https://github.com/Engelberg/instaparse#parse-errors

  `opts` are as for parse"
  ([s] (parse-with-info s nil))
  ([^String s {:keys [normalise-gpl-ids?
                      case-sensitive-operators?]
                 :or {normalise-gpl-ids?        true
                      case-sensitive-operators? false}}]
   (when-not (s/blank? s)
     (let [parser           (if case-sensitive-operators? @spdx-license-expression-cs-parser-d @spdx-license-expression-ci-parser-d)
           raw-parse-result (insta/parse parser s)]
       (if (insta/failure? raw-parse-result)
         raw-parse-result
         (let [transformed-result (insta/transform {:license-id            #(hash-map  :license-id           (get @normalised-spdx-ids-map-d (s/lower-case (first %&)) (first %&)))
                                                    :license-exception-id  #(hash-map  :license-exception-id (get @normalised-spdx-ids-map-d (s/lower-case (first %&)) (first %&)))
                                                    :license-ref           #(case (count %&)
                                                                              1 {:license-ref  (first %&)}
                                                                              2 {:document-ref (first %&) :license-ref (second %&)})
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
                                                   raw-parse-result)]
             (if normalise-gpl-ids?
               (normalise-gpl-elements transformed-result)
               transformed-result)))))))

#_{:clj-kondo/ignore [:unused-binding]}
(defn parse
  "Attempt to parse `s` (a String) as an SPDX license expression, returning a
  data structure representing the parse tree, or nil if it cannot be parsed.

  The optional `opts` map has these keys:
  * `normalise-gpl-ids?` (boolean, default true) - controls whether
    deprecated 'historical oddity' GPL family ids in the expression are
    normalised to their non-deprecated replacements as part of the parsing
    process.
  * `case-sensitive-operators?` (boolean, default false) - controls whether
    operators in expressions (AND, OR, WITH) are case-sensitive
    (spec-compliant, but strict) or not (non-spec-compliant, lenient).

  Notes:
  * The parser always normalises SPDX ids to their canonical case
    e.g. aPAcHe-2.0 -> Apache-2.0

  * The parser always removes redundant grouping
    e.g. (((((Apache-2.0)))))) -> Apache-2.0

  * The parser synthesises grouping when needed to make SPDX license
    expressions' precedence rules explicit (see
    https://spdx.github.io/spdx-spec/v2.3/SPDX-license-expressions/#d45-order-of-precedence-and-parentheses)

  * The default options result in parsing that is more lenient than the SPDX
    specification and that is therefore not strictly spec compliant.  You can
    enable strictly compliant parsing by setting `normalise-gpl-ids?` to `false`
    and `case-sensitive-operators?` to `true`.

  Examples (assuming default options):

  \"Apache-2.0\"
  -> {:license-id \"Apache-2.0\"}

  \"Apache-2.0+\"
  -> {:license-id \"Apache-2.0\" :or-later? true}

  \"GPL-2.0+\"
  -> {:license-id \"GPL-2.0-or-later\"}

  \"GPL-2.0 WITH Classpath-exception-2.0\"
  -> {:license-id \"GPL-2.0-only\"
      :license-exception-id \"Classpath-exception-2.0\"}

  \"CDDL-1.1 or (GPL-2.0+ with Classpath-exception-2.0)\"
  -> [:or
      {:license-id \"CDDL-1.1\"}
      {:license-id \"GPL-2.0-or-later\"
       :license-exception-id \"Classpath-exception-2.0\"}]

  \"DocumentRef-foo:LicenseRef-bar\")
  -> {:document-ref \"foo\"
      :license-ref \"bar\"}

  See SPDX Specification Annex D for more details on SPDX license expressions:
  https://spdx.github.io/spdx-spec/v2.3/SPDX-license-expressions/"
  ([s] (parse s nil))
  ([s {:keys [normalise-gpl-ids?
              case-sensitive-operators?]
         :or {normalise-gpl-ids?           true
              case-sensitive-operators? false}
         :as opts}]
   (when-let [raw-parse-result (parse-with-info s opts)]
     (when-not (insta/failure? raw-parse-result)
       raw-parse-result))))

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
        (str (:license-id parse-result)
             (when (:or-later? parse-result) "+")
             (when (:license-exception-id parse-result) (str " WITH " (:license-exception-id parse-result)))
             (when (:license-ref parse-result) (str (when (:document-ref parse-result) (str "DocumentRef-" (:document-ref parse-result) ":"))
                                                    "LicenseRef-" (:license-ref parse-result)))))))

(defn unparse
  "Turns a valid `parse-result` (i.e. obtained from `parse`) back into a
  canonicalised SPDX expression (a String).  Results are undefined for invalid
  parse trees.  Returns nil if `parse-result` is nil.

  Canonicalisation involves:
  * Converting all SPDX listed identifiers to their official case
  * Upper casing all operators
  * Removing redundant grouping (parens)
  * Adding grouping (parens) to make precedence rules explicit
  * (with default options) Normalising deprecated 'historical oddity' GPL family
    ids to their non-deprecated replacements"
  [parse-result]
  (when-let [result (unparse-internal 0 parse-result)]
    (when-not (s/blank? result)
      (s/trim result))))

#_{:clj-kondo/ignore [:unused-binding]}
(defn normalise
  "Normalises an SPDX expression, by running it through parse then unparse.
  Returns nil if `s` is nil or is not a valid SPDX expression.

  `opts` are as for parse"
  ([s] (normalise s nil))
  ([s opts]
   (some-> s
           (parse opts)
           unparse)))

(defn valid?
  "Is `s` (a String) a valid SPDX license expression?

  Note: if you intend to parse `s` if it's valid, it's more efficient to call
  parse directly and check for a nil result instead of calling this method
  first (doing so avoids double parsing).

  The optional `opts` map has these keys:
  * `case-sensitive-operators?` (boolean, default false) - controls whether
    operators in expressions (AND, OR, WITH) are case-sensitive
    (spec-compliant, but strict) or not (non-spec-compliant, lenient)."
  ([^String s] (valid? s nil))
  ([^String s {:keys [case-sensitive-operators?]
                 :or {case-sensitive-operators? false}}]
   (let [parser (if case-sensitive-operators? @spdx-license-expression-cs-parser-d @spdx-license-expression-ci-parser-d)]
     (not (or (s/blank? s)
              (insta/failure? (insta/parse parser s)))))))

(defn extract-ids
  "Extract all SPDX ids (as a set of strings) from the given `parse-result`.

  The optional `opts` map has these keys:
  * include-or-later? (boolean, default false) - controls whether the output
    includes the 'or later' indicator ('+') after license ids that have that
    designation in the parse tree."
  ([parse-result] (extract-ids parse-result nil))
  ([parse-result  {:keys [include-or-later?] :or {include-or-later? false} :as opts}]
   (when parse-result
     (cond
       (sequential? parse-result) (set (mapcat #(extract-ids % opts) parse-result))  ; Note: naive (stack consuming) recursion
       (map?        parse-result) (set/union (when (:license-id           parse-result) #{(str (:license-id parse-result) (when (and include-or-later? (:or-later? parse-result)) "+"))})
                                             (when (:license-exception-id parse-result) #{(:license-exception-id parse-result)})
                                             (when (:license-ref          parse-result)
                                               #{(str (when (:document-ref parse-result) (str "DocumentRef-" (:document-ref parse-result) ":"))
                                                      "LicenseRef-" (:license-ref parse-result))}))
       :else        nil))))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning nil. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it."
  []
  (lic/init!)
  (exc/init!)
  @license-ids-fragment
  @exception-ids-fragment
  @normalised-spdx-ids-map-d
; Note: we always leave these to runtime, since they're not expensive, and doing so
; ensures that callers who exclusively use one parsing variant aren't paying an
; unnecessary cost.
;  @spdx-license-expression-ci-grammar-d
;  @spdx-license-expression-cs-grammar-d
;  @spdx-license-expression-ci-parser-d
;  @spdx-license-expression-cs-parser-d
  nil)
