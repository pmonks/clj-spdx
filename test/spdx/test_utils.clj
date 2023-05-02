;
; Copyright © 2023 Peter Monks
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;
; SPDX-License-Identifier: Apache-2.0
;

(ns spdx.test-utils
  (:require [clojure.string :as s]))

; Environment variable to control whether all slow tests are run or not - see https://github.com/spdx/Spdx-Java-Library/blob/master/src/test/java/org/spdx/utility/compare/UnitTestHelper.java#L44-L51
(def run-all-slow-tests? (boolean (when-let [rst (System/getenv "SPDX_CLJ_LIB_RUN_SLOW_TESTS")] (parse-boolean (s/lower-case rst)))))

(if run-all-slow-tests?
  (println "🐢 Running slow tests - this will likely take at least an hour!")
  (println "🐇 Skipping slow tests - this should only take a minute or two.\nTo run all tests set env var SPDX_CLJ_LIB_RUN_SLOW_TESTS to 'true'."))

(defn equivalent-colls?
  "Are all of the colls 'equivalent' (same values and occurrences of each value, but in any order and regardless of concrete collection type)?"
  [& colls]
  (apply = (map frequencies colls)))
