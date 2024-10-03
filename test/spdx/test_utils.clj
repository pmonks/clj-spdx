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
  (:require [urlocal.api :as url]))

(println "\n☔️ Running tests on Clojure" (clojure-version) "/ JVM" (System/getProperty "java.version") (str "(" (System/getProperty "java.vm.name") " v" (System/getProperty "java.vm.version") ")\n"))

(url/set-cache-name! "clj-spdx-tests")
(url/set-cache-check-interval-secs! 604800)  ; 1 week

(println "ℹ️ These unit tests take several minutes to complete, in the best case")

(defn equivalent-colls?
  "Are all of the colls 'equivalent' (same values and occurrences of each value,
  but in any order and regardless of concrete collection type)?"
  [& colls]
  (apply = (map frequencies colls)))

(defn http-get
  "HTTP GET the given URL (a `String`, `java.netURL` or `java.net.URI`),
  returning an `InputStream` for the content at that location. Utilises caching
  and efficient HTTP requests internally to minimise network I/O.

  Throws on exceptions."
  [url]
  (url/input-stream url {:follow-redirects?     true
                         :retry-when-throttled? true
                         :request-headers       {"User-Agent" "https://github.com/pmonks/clj-spdx"}}))
