;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.regexes-test
  (:require [clojure.test    :refer [deftest testing is]]
            [rencg.api       :as rencg]
            [spdx.licenses   :as slic]
            [spdx.exceptions :as sexc]
            [spdx.test-utils]  ; Unused here, but we force it to run first
            [spdx.regexes    :refer [build-re ids-re license-ids-re exception-ids-re license-ref-re addition-ref-re]]))

(deftest build-re-tests
  (testing "Basic tests"
    (is (nil? (build-re nil)))
    (is (nil? (build-re nil {:include-license-refs? true})))
    (is (nil? (build-re nil {:include-addition-refs? true})))
    (is (nil? (build-re nil {:include-license-refs? true :include-addition-refs? true})))
    (is (nil? (build-re nil {:case-sensitive? true :include-license-refs? true :include-addition-refs? true})))
    (is (instance? java.util.regex.Pattern (build-re ["Apache-2.0"]))))
  (let [apache-20-re (build-re ["Apache-2.0"])]
    (testing "matches"
      (is (not (nil? (re-matches apache-20-re "Apache-2.0"))))
      (is (not (nil? (re-matches apache-20-re "apache-2.0")))))
    (testing "non-matches"
      (is (nil? (re-matches apache-20-re "")))
      (is (nil? (re-matches apache-20-re "foobar")))
      (is (nil? (re-matches apache-20-re " Apache-2.0 ")))
      (is (nil? (re-matches apache-20-re "Apache-200")))
      (is (nil? (re-matches apache-20-re "GPL-2.0")))
      (is (nil? (re-matches apache-20-re "Apache-1.1")))
      (is (nil? (re-matches apache-20-re "Apache-2.0 GPL-2.0")))
      (is (nil? (re-matches apache-20-re "LicenseRef-foo-bar"))))
    (testing "finds"
      (is (not (nil? (re-find apache-20-re "Apache-2.0"))))
      (is (not (nil? (re-find apache-20-re "APACHE-2.0"))))
      (is (not (nil? (re-find apache-20-re " Apache-2.0 "))))
      (is (not (nil? (re-find apache-20-re "foo Apache-2.0"))))
      (is (not (nil? (re-find apache-20-re "Apache-2.0 bar"))))
      (is (not (nil? (re-find apache-20-re "foo Apache-2.0 bar"))))
      (is (not (nil? (re-find apache-20-re "foo;Apache-2.0;bar"))))
      (is (not (nil? (re-find apache-20-re "Apache-1.1 Apache-2.0 GPL-2.0")))))
    (testing "non-finds"
      (is (nil? (re-find apache-20-re "")))
      (is (nil? (re-find apache-20-re "foobar")))
      (is (nil? (re-find apache-20-re "GPL-2.0")))
      (is (nil? (re-find apache-20-re "Apache-200")))
      (is (nil? (re-find apache-20-re "fooApache-2.0")))
      (is (nil? (re-find apache-20-re "Apache-2.0bar")))
      (is (nil? (re-find apache-20-re "fooApache-2.0bar")))
      (is (nil? (re-find apache-20-re "foo;Apache-2.0bar")))
      (is (nil? (re-find apache-20-re "fooApache-2.0;bar"))))
    (testing "re-seq"
      (is (= 0 (count (re-seq apache-20-re ""))))
      (is (= 0 (count (re-seq apache-20-re "foobar"))))
      (is (= 1 (count (re-seq apache-20-re "Apache-2.0"))))
      (is (= 2 (count (re-seq apache-20-re "Apache-2.0 Apache-2.0"))))
      (is (= 2 (count (re-seq apache-20-re "foo;Apache-2.0 bar Apache-2.0 blah"))))))
  (let [apache-20-re (build-re ["Apache-2.0"] {:case-sensitive? true})]
    (testing "matches - case sensitive"
      (is (not (nil? (re-matches apache-20-re "Apache-2.0")))))
    (testing "non-matches - case sensitive"
      (is (nil? (re-matches apache-20-re "")))
      (is (nil? (re-matches apache-20-re "apache-2.0")))
      (is (nil? (re-matches apache-20-re "APACHE-2.0"))))
    (testing "finds - case sensitive"
      (is (not (nil? (re-find apache-20-re "Apache-2.0"))))
      (is (not (nil? (re-find apache-20-re " Apache-2.0 "))))
      (is (not (nil? (re-find apache-20-re "foo;Apache-2.0;bar"))))
      (is (not (nil? (re-find apache-20-re "Apache-1.1 Apache-2.0 GPL-2.0")))))
    (testing "non-finds - case sensitive"
      (is (nil? (re-find apache-20-re "APACHE-2.0")))))
  ; Because the ids that end in a + are a headache
  (let [gpl-20-plus-re (build-re ["GPL-2.0+"])]
    (testing "GPL-2.0+ matches"
      (is (not (nil? (re-matches gpl-20-plus-re "GPL-2.0+")))))
    (testing "GPL-2.0+ non-matches"
      (is (nil? (re-matches gpl-20-plus-re "")))
      (is (nil? (re-matches gpl-20-plus-re "foobar")))
      (is (nil? (re-matches gpl-20-plus-re " GPL-2.0+ ")))
      (is (nil? (re-matches gpl-20-plus-re "GPL-200+")))
      (is (nil? (re-matches gpl-20-plus-re "GPL-2.0")))
      (is (nil? (re-matches gpl-20-plus-re "GPL-1.0+")))
      (is (nil? (re-matches gpl-20-plus-re "Apache-2.0 GPL-2.0+")))
      (is (nil? (re-matches gpl-20-plus-re "LicenseRef-foo-bar"))))
    (testing "GPL-2.0+ finds"
      (is (not (nil? (re-find gpl-20-plus-re "GPL-2.0+"))))
      (is (not (nil? (re-find gpl-20-plus-re "GPL-2.0++"))))
      (is (not (nil? (re-find gpl-20-plus-re "GPL-2.0++cpe"))))
      (is (not (nil? (re-find gpl-20-plus-re " GPL-2.0+ "))))
      (is (not (nil? (re-find gpl-20-plus-re "foo GPL-2.0+"))))
      (is (not (nil? (re-find gpl-20-plus-re "GPL-2.0+ bar"))))
      (is (not (nil? (re-find gpl-20-plus-re "foo GPL-2.0+ bar"))))
      (is (not (nil? (re-find gpl-20-plus-re "foo;GPL-2.0+;bar"))))
      (is (not (nil? (re-find gpl-20-plus-re "Apache-1.1 GPL-2.0+ Apache-2.0")))))
    (testing "GPL-2.0+ non-finds"
      (is (nil? (re-find gpl-20-plus-re "")))
      (is (nil? (re-find gpl-20-plus-re "foobar")))
      (is (nil? (re-find gpl-20-plus-re "GPL-2.0")))
      (is (nil? (re-find gpl-20-plus-re "fooGPL-2.0+")))
      (is (nil? (re-find gpl-20-plus-re "GPL-2.0+bar")))
      (is (nil? (re-find gpl-20-plus-re "fooGPL-2.0+bar")))
      (is (nil? (re-find gpl-20-plus-re "foo;GPL-2.0+bar")))
      (is (nil? (re-find gpl-20-plus-re "fooGPL-2.0+;bar")))))
  (let [apache-20-or-license-ref-re (build-re ["Apache-2.0"] {:include-license-refs? true})]
    (testing "matches"
      (is (not (nil? (re-matches apache-20-or-license-ref-re "Apache-2.0"))))
      (is (not (nil? (re-matches apache-20-or-license-ref-re "aPaChE-2.0"))))
      (is (not (nil? (re-matches apache-20-or-license-ref-re "LicenseRef-foo-bar")))))
    (testing "non-matches"
      (is (nil? (re-matches apache-20-or-license-ref-re "licenseref-foo-bar"))))
    (testing "Named capturing groups (via rencg library)"
      (let [m (rencg/re-matches-ncg apache-20-or-license-ref-re "Apache-2.0")]
        (is (contains? m "Identifier"))
        (is (not (contains? m "DocumentRef")))
        (is (not (contains? m "LicenseRef")))
        (is (= "Apache-2.0" (get m "Identifier"))))
      (let [m (rencg/re-matches-ncg apache-20-or-license-ref-re "LicenseRef-foo-bar")]
        (is (contains? m "Identifier"))
        (is (contains? m "LicenseRef"))
        (is (not (contains? m "DocumentRef")))
        (is (= "LicenseRef-foo-bar" (get m "Identifier")))
        (is (= "foo-bar"            (get m "LicenseRef"))))
      (let [m (rencg/re-matches-ncg apache-20-or-license-ref-re "DocumentRef-foo:LicenseRef-bar")]
        (is (contains? m "Identifier"))
        (is (contains? m "LicenseRef"))
        (is (contains? m "DocumentRef"))
        (is (= "DocumentRef-foo:LicenseRef-bar" (get m "Identifier")))
        (is (= "bar"                            (get m "LicenseRef")))
        (is (= "foo"                            (get m "DocumentRef")))))))

(def non-ref-values-that-should-find-and-match
  (concat ["apache-2.0"
           "mIt"]
          (filter #(not (re-matches #".*\+\z" %)) (slic/ids))   ; Strip out the + variants TODO: PUT THEM IN A SEPARATE LIST WITH SEPARATE TESTS!!!!
          (sexc/ids)))

(def ref-values-that-should-find-and-match
  ["LicenseRef-foo"
   "LicenseRef-foo-"                              ; valid, but 🤢
   "LicenseRef-foo."                              ; valid, but 🤢
   "LicenseRef-foo.-.-.-.-.-.-.-.-.-.-.-.-bar"    ; valid, but 🤢
   "DocumentRef-foo:LicenseRef-bar"
   "DocumentRef-foo-:LicenseRef-bar."             ; valid, but 🤢
   "AdditionRef-foo"
   "AdditionRef-foo-."                            ; valid, but 🤢
   "DocumentRef-foo:AdditionRef-bar"
   "DocumentRef-foo.:AdditionRef-bar-"])          ; valid, but 🤢

(def values-that-should-find-and-match
  (concat non-ref-values-that-should-find-and-match
          ref-values-that-should-find-and-match
          (map #(str % "-version-2.0") ref-values-that-should-find-and-match)))  ; Extending any valid ref with [\p{Alnum}\-\.]+ will always result in a new, valid ref

(def values-that-should-find-but-not-match
  (concat ["-Apache-2.0-"
           "Apache-1.1 apache-2.0 GPL-2.0"]
          (map #(str " " %)                 values-that-should-find-and-match)
          (map #(str % " ")                 values-that-should-find-and-match)
          (map #(str "prefix " %)           values-that-should-find-and-match)
          (map #(str % " suffix")           values-that-should-find-and-match)
          (map #(str "prefix " % " suffix") values-that-should-find-and-match)
          (map #(str "prefix;" %)           values-that-should-find-and-match)
          (map #(str % ";suffix")           values-that-should-find-and-match)
          (map #(str "prefix;" % ";suffix") values-that-should-find-and-match)
          (map #(str "prefix." %)           values-that-should-find-and-match)
          (map #(str % ".suffix")           non-ref-values-that-should-find-and-match)  ; Extending any valid ref with [\p{Alnum}\-\.]+ will always result in a new, valid ref
          (map #(str "prefix." % ".suffix") values-that-should-find-and-match)
          (map #(str "prefix-" %)           values-that-should-find-and-match)
          (map #(str % "-suffix")           non-ref-values-that-should-find-and-match)))  ; Extending any valid ref with [\p{Alnum}\-\.]+ will always result in a new, valid ref

(def values-that-should-not-find-or-match
  ["foobar"
   "Apache-2.00"
   "licenseref-foo"
   "additionaref-foo"
   "documentRef-foo:Licenseref-bar"
   "documentRef-foo:Additionref-bar"])

(def values-that-should-not-match
  (concat values-that-should-not-find-or-match
          (map #(str "foo" %) values-that-should-find-and-match)
          (map #(str % "bar") non-ref-values-that-should-find-and-match)))  ; Extending any valid ref with [\p{Alnum}\-\.]+ will always result in a new, valid ref

(deftest ids-re-tests
  (testing "Basic tests"
    (is (not (nil? (ids-re))))
    (is (instance? java.util.regex.Pattern (ids-re)))
    (is (= (ids-re) (ids-re))))  ; Ensure regex is cached
  (testing "Values that should match"
    (run! #(is (not (nil? (re-matches (ids-re) %))) %) values-that-should-find-and-match))
  (testing "Values that should not match"
    (run! #(is (nil? (re-matches (ids-re) %)) %) values-that-should-find-but-not-match)
    (run! #(is (nil? (re-matches (ids-re) %)) %) values-that-should-not-match))
  (testing "Values that should find"
    (run! #(is (not (nil? (re-find (ids-re) %))) %) values-that-should-find-and-match)
    (run! #(is (not (nil? (re-find (ids-re) %))) %) values-that-should-find-but-not-match))
  (testing "Values that should not find"
    (run! #(is (nil? (re-find (ids-re) %)) %) values-that-should-not-find-or-match)))

; We keep these short as most variations are exercised via build-re-test
(deftest license-ids-re-tests
  (testing "Basic tests"
    (is (not (nil? (license-ids-re))))
    (is (instance? java.util.regex.Pattern (license-ids-re)))
    (is (= (license-ids-re) (license-ids-re))))  ; Ensure regex is cached
  (testing "matches"
    (is (not (nil? (re-matches (license-ids-re) "Apache-2.0"))))
    (is (not (nil? (re-matches (license-ids-re) "MIT"))))
    (is (not (nil? (re-matches (license-ids-re) "GPL-2.0"))))
    (is (not (nil? (re-matches (license-ids-re) "LicenseRef-foo"))))
    (is (not (nil? (re-matches (license-ids-re) "DocumentRef-foo:LicenseRef-bar")))))
  (testing "non-matches"
    (is (nil? (re-matches (license-ids-re) "Apache-200")))
    (is (nil? (re-matches (license-ids-re) " Apache-2.0 ")))
    (is (nil? (re-matches (license-ids-re) "Apache-2.0 GPL-2.0")))
    (is (nil? (re-matches (license-ids-re) "foobar")))
    (is (nil? (re-matches (license-ids-re) "Classpath-exception-2.0")))
    (is (nil? (re-matches (license-ids-re) "AdditionRef-foo")))
    (is (nil? (re-matches (license-ids-re) "DocumentRef-foo:AdditionRef-bar")))))

; We keep these short as most variations are exercised via build-re-test
(deftest exception-ids-re-tests
  (testing "Basic tests"
    (is (not (nil? (exception-ids-re))))
    (is (instance? java.util.regex.Pattern (exception-ids-re)))
    (is (= (exception-ids-re) (exception-ids-re))))  ; Ensure regex is cached
  (testing "matches"
    (is (not (nil? (re-matches (exception-ids-re) "Classpath-exception-2.0"))))
    (is (not (nil? (re-matches (exception-ids-re) "Autoconf-exception-3.0"))))
    (is (not (nil? (re-matches (exception-ids-re) "AdditionRef-foo"))))
    (is (not (nil? (re-matches (exception-ids-re) "DocumentRef-foo:AdditionRef-bar")))))
  (testing "non-matches"
    (is (nil? (re-matches (exception-ids-re) "Classpath-exception-200")))
    (is (nil? (re-matches (exception-ids-re) " Classpath-exception-2.0 ")))
    (is (nil? (re-matches (exception-ids-re) "Classpath-exception-2.0 Autoconf-exception-3.0")))
    (is (nil? (re-matches (exception-ids-re) "foobar")))
    (is (nil? (re-matches (exception-ids-re) "Apache-2.0")))
    (is (nil? (re-matches (exception-ids-re) "MIT")))
    (is (nil? (re-matches (exception-ids-re) "GPL-2.0")))
    (is (nil? (re-matches (exception-ids-re) "LicenseRef-foo")))
    (is (nil? (re-matches (exception-ids-re) "DocumentRef-foo:LicenseRef-bar")))))

(deftest license-ref-re-tests
  (testing "Basic tests"
    (is (not (nil? (license-ref-re))))
    (is (instance? java.util.regex.Pattern (license-ref-re)))
    (is (= (license-ref-re) (license-ref-re))))  ; Ensure regex is cached
  (testing "matches"
    (is (not (nil? (re-matches (license-ref-re) "LicenseRef-foo"))))
    (is (not (nil? (re-matches (license-ref-re) "DocumentRef-foo:LicenseRef-bar"))))
    (is (not (nil? (re-matches (license-ref-re) "DocumentRef-foo-bar-2.0:LicenseRef-foo-bar-2.0")))))
  (testing "non-matches"
    (is (nil? (re-matches (license-ref-re) "licenseref-foo")))
    (is (nil? (re-matches (license-ref-re) "documentRef-foo:Licenseref-bar")))
    (is (nil? (re-matches (license-ref-re) "LicenseRef-@%$^")))
    (is (nil? (re-matches (license-ref-re) "Apache-2.0")))
    (is (nil? (re-matches (license-ref-re) "Apache-200")))
    (is (nil? (re-matches (license-ref-re) " Apache-2.0 ")))
    (is (nil? (re-matches (license-ref-re) "Apache-2.0 GPL-2.0")))
    (is (nil? (re-matches (license-ref-re) "foobar")))
    (is (nil? (re-matches (license-ref-re) "Classpath-exception-2.0")))
    (is (nil? (re-matches (license-ref-re) "AdditionRef-foo")))
    (is (nil? (re-matches (license-ref-re) "DocumentRef-foo:AdditionRef-bar")))))

(deftest addition-ref-re-tests
  (testing "Basic tests"
    (is (not (nil? (addition-ref-re))))
    (is (instance? java.util.regex.Pattern (addition-ref-re)))
    (is (= (addition-ref-re) (addition-ref-re))))  ; Ensure regex is cached
  (testing "matches"
    (is (not (nil? (re-matches (addition-ref-re) "AdditionRef-foo"))))
    (is (not (nil? (re-matches (addition-ref-re) "DocumentRef-foo:AdditionRef-bar"))))
    (is (not (nil? (re-matches (addition-ref-re) "DocumentRef-foo-bar-2.0:AdditionRef-foo-bar-2.0"))))))
  (testing "non-matches"
    (is (nil? (re-matches (addition-ref-re) "additionref-foo")))
    (is (nil? (re-matches (addition-ref-re) "documentRef-foo:Additionref-bar")))
    (is (nil? (re-matches (addition-ref-re) "AdditionRef-@%$^")))
    (is (nil? (re-matches (addition-ref-re) "Apache-2.0")))
    (is (nil? (re-matches (addition-ref-re) "Apache-200")))
    (is (nil? (re-matches (addition-ref-re) " Apache-2.0 ")))
    (is (nil? (re-matches (addition-ref-re) "Apache-2.0 GPL-2.0")))
    (is (nil? (re-matches (addition-ref-re) "foobar")))
    (is (nil? (re-matches (addition-ref-re) "Classpath-exception-2.0")))
    (is (nil? (re-matches (addition-ref-re) "LicenseRef-foo")))
    (is (nil? (re-matches (addition-ref-re) "DocumentRef-foo:LicenseRef-bar"))))
