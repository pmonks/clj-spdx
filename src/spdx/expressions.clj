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

; Adapted from ABNF grammar at https://spdx.github.io/spdx-spec/v2.3/SPDX-license-expressions/
(def ^:private spdx-license-expression-grammar-format "
  (* Primitive tokens *)
  <ws>                   = <#\"\\s+\">
  <ows>                  = <#\"\\s*\">
  <id-string>            = #\"[\\p{Alnum}-\\.]+\"
  and                    = <ws 'AND' ws>
  or                     = <ws 'OR' ws>
  <with>                 = <ws 'WITH' ws>
  <or-later>             = <'+'>

  (* Identifiers *)
  license-id             = %s
  license-exception-id   = %s
  license-ref            = [<'DocumentRef-'> id-string <':'>] <'LicenseRef-'> id-string

  (* Composite expressions *)
  license-or-later       = license-id or-later
  <simple-expression>    = license-id | license-or-later | license-ref
  with-expression        = simple-expression with license-exception-id
  <composite-expression> = compound-expression ((and|or) compound-expression)+
  nested-expression      = <'('> ows compound-expression ows <')'>
  <compound-expression>  = simple-expression | with-expression | composite-expression | nested-expression

  (* Start rule *)
  <license-expression>   = ows compound-expression ows
  ")

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

(def ^:private spdx-license-expression-grammar-d (delay (format spdx-license-expression-grammar-format
                                                                (s/join " | " (map #(str "#\"(?i)" (escape-re %) "\"") (filter #(not (s/ends-with? % "+")) (lic/ids))))  ; Filter out the few deprecated ids that end in "+", since they break the parser
                                                                (s/join " | " (map #(str "#\"(?i)" (escape-re %) "\"") (exc/ids))))))
(def ^:private spdx-license-expression-parser-d  (delay (insta/parser @spdx-license-expression-grammar-d :start :license-expression)))

(def ^:private normalised-spdx-ids-map-d (delay (merge (into {} (map #(vec [(s/lower-case %) %]) (lic/ids)))
                                                       (into {} (map #(vec [(s/lower-case %) %]) (exc/ids))))))

(defn parse-with-info
  "As for parse, but returns instaparse parse error info if parsing fails,
  instead of nil.

  See also https://github.com/Engelberg/instaparse#parse-errors"
  [^String s]
  (when-not (s/blank? s)
    (let [raw-parse-result (insta/parse @spdx-license-expression-parser-d s)]
      (if (insta/failure? raw-parse-result)
        raw-parse-result
        (let [transformed-result (insta/transform {:and                   (constantly :and)
                                                   :or                    (constantly :or)
                                                   :license-id            #(hash-map  :license-id           (get @normalised-spdx-ids-map-d (s/lower-case (first %&)) (first %&)))
                                                   :license-exception-id  #(hash-map  :license-exception-id (get @normalised-spdx-ids-map-d (s/lower-case (first %&)) (first %&)))
                                                   :license-ref           #(case (count %&)
                                                                             1 {:license-ref (first %&)}
                                                                             2 {:document-ref (first %&) :license-ref (second %&)})
                                                   :license-or-later      #(merge     {:or-later true}      (first %&))
                                                   :with-expression       #(merge     (first %&) (second %&))
                                                   :nested-expression     #(case (count %&)
                                                                             1  (first %&)     ; We do this to "collapse" redundant nesting e.g. "(((Apache-2.0)))"
                                                                             (vec %&))}
                                                  raw-parse-result)]
          (if (sequential? transformed-result)
            (case (count transformed-result)
              1 (first transformed-result)
              (vec transformed-result))
            transformed-result))))))

(defn parse
  "Attempt to parse the given string as an SPDX license expression, returning a
  data structure representing the parse tree or nil if the string cannot be
  parsed.

  See SPDX Specification Annex D for details on SPDX license expressions:
  https://spdx.github.io/spdx-spec/v2.3/SPDX-license-expressions/

  Notes:
  * The parser normalises SPDX ids to their canonical case
    e.g. aPAcHe-2.0 -> Apache-2.0

  * The parser removes redundant grouping
    e.g. (((((Apache-2.0)))))) -> Apache-2.0

  * When a license is modified with the \"or later\" modifier ('+'), the two
    are grouped

  * When a license is modified WITH a license exception, the two are grouped

  Examples:

  \"Apache-2.0\"
  -> {:license-id \"Apache-2.0\"}

  \"GPL-2.0+\"
  -> {:license-id \"GPL-2.0\" :or-later true}

  \"GPL-2.0 WITH Classpath-exception-2.0\"
  -> {:license-id \"GPL-2.0\"
      :license-exception-id \"Classpath-exception-2.0\"}

  \"CDDL-1.1 OR (GPL-2.0+ WITH Classpath-exception-2.0)\"
  -> [{:license-id \"CDDL-1.1\"}
      :or
      {:license-id \"GPL-2.0\"
       :or-later true
       :license-exception-id \"Classpath-exception-2.0\"}]"
  [^String s]
  (when-let [raw-parse-result (parse-with-info s)]
    (when-not (insta/failure? raw-parse-result)
      raw-parse-result)))

(defn- build-license-id
  "Builds the correct final license id for the given license id and 'or-later'
  indicator.  This is primarily to handle the *GPL family's 'or-later' and
  'only' suffixes (which are a pita)."
  ([license-id or-later] (build-license-id license-id or-later true))
  ([license-id or-later include-or-later]
   (if or-later
     (let [or-later-variant (str license-id "-or-later")]
       (if (and (lic/listed-id?          or-later-variant)
                (not (lic/deprecated-id? or-later-variant)))
         or-later-variant
         (str license-id (when include-or-later "+"))))
     (let [only-variant (str license-id "-only")]
       (if (and (lic/listed-id?          only-variant)
                (not (lic/deprecated-id? only-variant)))
         only-variant
         license-id)))))

(defn- unparse-internal
  "Internal, naively recursive implementation of unparse."
  [parse-result]
  (when parse-result
    (cond
      (= :or       parse-result) "OR"
      (= :and      parse-result) "AND"
      (sequential? parse-result) (when (pos? (count parse-result)) (str "(" (s/join " " (map unparse-internal parse-result)) ")"))
      (map?        parse-result) (str (build-license-id (:license-id parse-result) (:or-later parse-result))
                                      (when (:license-exception-id parse-result) (str " WITH " (:license-exception-id parse-result)))
                                      (when (:license-ref parse-result) (str (when (:document-ref parse-result) (str "DocumentRef-" (:document-ref parse-result) ":"))
                                                                             "LicenseRef-" (:license-ref parse-result))))
      :else       nil)))

(defn unparse
  "Turns a (successful) parse result back into a (normalised) SPDX expression
  string. Results are undefined for invalid parse trees.

  Note that the GPL family of licenses have special handling, whereby suffixes
  are always added. This is because the non-suffixed GPL family license ids
  have been deprecated in the SPDX license list. Examples:
  * GPL-2.0   -> GPL-2.0-only
  * AGPL-3.0+ -> AGPL-3.0-or-later"
  [parse-result]
  (when parse-result
    (when-let [result (if (sequential? parse-result)
                        (s/join " " (map unparse-internal parse-result))
                        (unparse-internal parse-result))]
      (when-not (s/blank? result)
        (s/trim result)))))

(defn normalise
  "'Normalises' an SPDX expression, by running it through parse then unparse.
  Returns nil if s is nil or is not a valid SPDX expression."
  [s]
  (some-> s
          parse
          unparse))

(defn valid?
  "Is the given string a valid SPDX license expression?

  Note: if you intend to parse the given string if it's valid, it's more
  efficient to call parse directly and check for a nil result."
  [^String s]
  (not (or (s/blank? s)
           (insta/failure? (insta/parse @spdx-license-expression-parser-d s)))))

(defn extract-ids
  "Extract all SPDX ids (as a set of strings) from the given parse result,
  optionally including the 'or later' indicator ('+') after license ids that
  have that designation in the parse tree (defaults to false).

  Note: license 'families' that provide 'or-later' suffixed variants (i.e.
  *GPL licenses) will always end up with either the 'or-later' or the 'only'
  suffix version of an id, regardless of the value of the include-or-later
  flag.  This is because the 'naked' variants of these license ids (e.g.
  'GPL-2.0') are deprecated in the SPDX license list, and their use is
  discouraged. See https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated
  for more details."
  ([parse-result] (extract-ids parse-result false))
  ([parse-result include-or-later]
   (when parse-result
     (cond
       (sequential? parse-result) (set (mapcat #(extract-ids % include-or-later) parse-result))
       (map?        parse-result) (set/union (when (:license-id           parse-result) #{(build-license-id (:license-id parse-result) (:or-later parse-result) include-or-later)})
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
  @spdx-license-expression-grammar-d
  @spdx-license-expression-parser-d
  @normalised-spdx-ids-map-d
  nil)
