;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.test-utils
  (:require [spdx.identifiers  :as si]))

(println "\n☔️ Running tests on Clojure" (clojure-version)
         "/ JVM" (System/getProperty "java.version") (str "(" (System/getProperty "java.vm.name") " v" (System/getProperty "java.vm.version") ")")
         "/ SPDX license list" (str "v" (si/version)))

(println "ℹ️ These unit tests take several minutes to complete, in the best case")

(defn equivalent-colls?
  "Are all of the colls 'equivalent' (same values and occurrences of each value,
  but in any order and regardless of concrete collection type)?"
  [& colls]
  (apply = (map frequencies colls)))
