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

(ns spdx.test-utils)

(println "🐢 These tests take around 10 minutes.")

(defn equivalent-colls?
  "Are all of the colls 'equivalent' (same values and occurrences of each value, but in any order and regardless of concrete collection type)?"
  [& colls]
  (apply = (map frequencies colls)))
