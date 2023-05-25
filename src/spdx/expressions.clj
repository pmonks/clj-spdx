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
  "SPDX license expression functionality. This functionality is bespoke (it is not provided by Spdx-Java-Library)."
  (:require [clojure.string  :as s]
            [instaparse.core :as insta]
            [spdx.licenses   :as lic]
            [spdx.exceptions :as exc]))

; Adapted from ABNF grammar at https://spdx.github.io/spdx-spec/v2.3/SPDX-license-expressions/
(def ^:private spdx-license-expression-grammar-format "
  <ws>                  = <#\"\\s+\">
  <ows>                 = <#\"\\s*\">
  <id-string>           = #\"[\\p{Alnum}-\\.]+\"
  with                  = <ws 'WITH' ws>
  and                   = <ws 'AND' ws>
  or                    = <ws 'OR' ws>
  <or-later>            = <'+'>
  license-id            = %s
  license-exception-id  = %s
  license-ref           = ['DocumentRef-' id-string ':'] 'LicenseRef-' id-string
  license-or-later      = (license-id or-later)
  <simple-expression>   = license-id | license-or-later | license-ref
  <compound-expression> = simple-expression |
                          (simple-expression with license-exception-id) |
                          (compound-expression and compound-expression) |
                          (compound-expression or compound-expression) |
                          (<'('> license-expression <')'>)
  license-expression    = ows compound-expression ows
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
        (insta/transform
          {:with                 (constantly :with)
           :and                  (constantly :and)
           :or                   (constantly :or)
           :license-id           #(hash-map  :license-id           (get @normalised-spdx-ids-map-d (s/lower-case (first %&)) (first %&)))
           :license-exception-id #(hash-map  :license-exception-id (get @normalised-spdx-ids-map-d (s/lower-case (first %&)) (first %&)))
           :license-ref          #(hash-map  :license-ref          (s/join %&))
           :license-or-later     #(merge     {:or-later true}      (first %&))
           :license-expression   #(case (count %&)
                                     0  nil
                                     1  (let [f (first %&)]
                                          (if (and (coll? f) (not= :license-expression (first f)))
                                            [:license-expression f]
                                            f))
                                     [:license-expression (vec %&)])}
          raw-parse-result)))))

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

  Examples:

  \"Apache-2.0\"
  -> [:license-expression {:license-id \"Apache-2.0\"}]

  \"GPL-2.0+\"
  -> [:license-expression {:license-id \"GPL-2.0\" :or-later true}]

  \"GPL-2.0+ WITH Classpath-exception-2.0\"
  -> [:license-expression
       [{:license-id \"GPL-2.0\" :or-later true}
        :with
        {:license-exception-id \"Classpath-exception-2.0\"}]]

  \"CDDL-1.1 OR (GPL-2.0 WITH Classpath-exception-2.0)\"
  -> [:license-expression
       [{:license-id \"CDDL-1.1\"}
        :or
        [:license-expression
          [{:license-id \"GPL-2.0\"}
           :with
           {:license-exception-id \"Classpath-exception-2.0\"}]]]]"
  [^String s]
  (when-let [raw-parse-result (parse-with-info s)]
    (when-not (insta/failure? raw-parse-result)
      raw-parse-result)))

(defn valid?
  "Is the given string a valid SPDX license expression?

  Note: if you intend to parse the given string if it's valid, it's more
  efficient to call parse directly and check for a nil result."
  [^String s]
  (not (or (s/blank? s)
           (insta/failure? (insta/parse @spdx-license-expression-parser-d s)))))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning nil. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it."
  []
  (lic/init!)
  (exc/init!)
  @spdx-license-expression-grammar-d
  @spdx-license-expression-parser-d
  @normalised-spdx-ids-map-d
  nil)
