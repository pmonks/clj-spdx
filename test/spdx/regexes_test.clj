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
            [spdx.regexes    :refer [build-re ids-re license-ids-re exception-ids-re license-ref-re addition-ref-re id-seq-matches id-seq]]))

(deftest build-re-tests
  (testing "Basic tests"
    (is (nil? (build-re nil)))
    (is (nil? (build-re nil {:include-license-refs? true})))
    (is (nil? (build-re nil {:include-addition-refs? true})))
    (is (nil? (build-re nil {:include-special-forms? true})))
    (is (nil? (build-re nil {:include-license-refs? true :include-addition-refs? true :include-special-forms? true})))
    (is (instance? java.util.regex.Pattern (build-re ["Apache-2.0"]))))
  (let [test-re (build-re ["Apache-2.0"])]
    (testing "matches"
      (is (not (nil? (re-matches test-re "Apache-2.0"))))
      (is (not (nil? (re-matches test-re "apache-2.0")))))
    (testing "non-matches"
      (is (nil? (re-matches test-re "")))
      (is (nil? (re-matches test-re "foobar")))
      (is (nil? (re-matches test-re " Apache-2.0 ")))
      (is (nil? (re-matches test-re "Apache-2.0+")))   ; Regexes only match identifiers, not expressions ("Apache-2.0+" is an expression)
      (is (nil? (re-matches test-re "Apache-200")))
      (is (nil? (re-matches test-re "GPL-2.0")))
      (is (nil? (re-matches test-re "Apache-1.1")))
      (is (nil? (re-matches test-re "Apache-2.0 GPL-2.0")))
      (is (nil? (re-matches test-re "LicenseRef-foo")))
      (is (nil? (re-matches test-re "AdditionRef-bar"))))
    (testing "finds"
      (is (not (nil? (re-find test-re "Apache-2.0"))))
      (is (not (nil? (re-find test-re "APACHE-2.0"))))
      (is (not (nil? (re-find test-re " Apache-2.0 "))))
      (is (not (nil? (re-find test-re "foo Apache-2.0"))))
      (is (not (nil? (re-find test-re "Apache-2.0 bar"))))
      (is (not (nil? (re-find test-re "foo Apache-2.0 bar"))))
      (is (not (nil? (re-find test-re "foo;Apache-2.0;bar"))))
      (is (not (nil? (re-find test-re "Apache-1.1 Apache-2.0 GPL-2.0")))))
    (testing "non-finds"
      (is (nil? (re-find test-re "")))
      (is (nil? (re-find test-re "foobar")))
      (is (nil? (re-find test-re "GPL-2.0")))
      (is (nil? (re-find test-re "Apache-200")))
      (is (nil? (re-find test-re "fooApache-2.0")))
      (is (nil? (re-find test-re "Apache-2.0bar")))
      (is (nil? (re-find test-re "fooApache-2.0bar")))
      (is (nil? (re-find test-re "foo;Apache-2.0bar")))
      (is (nil? (re-find test-re "fooApache-2.0;bar"))))
    (testing "re-seq"
      (is (= 0 (count (re-seq test-re ""))))
      (is (= 0 (count (re-seq test-re "foobar"))))
      (is (= 1 (count (re-seq test-re "Apache-2.0"))))
      (is (= 2 (count (re-seq test-re "Apache-2.0 Apache-2.0"))))
      (is (= 2 (count (re-seq test-re "foo;Apache-2.0 bar Apache-2.0 blah"))))))
  ; Because the (deprecated) ids that end in a + are a headache
  (let [test-re (build-re ["GPL-2.0+"])]
    (testing "GPL-2.0+ matches"
      (is (not (nil? (re-matches test-re "GPL-2.0+")))))
    (testing "GPL-2.0+ non-matches"
      (is (nil? (re-matches test-re "")))
      (is (nil? (re-matches test-re "foobar")))
      (is (nil? (re-matches test-re " GPL-2.0+ ")))
      (is (nil? (re-matches test-re "GPL-200+")))
      (is (nil? (re-matches test-re "GPL-2.0")))
      (is (nil? (re-matches test-re "GPL-1.0+")))
      (is (nil? (re-matches test-re "Apache-2.0 GPL-2.0+")))
      (is (nil? (re-matches test-re "LicenseRef-foo-bar"))))
    (testing "GPL-2.0+ finds"
      (is (not (nil? (re-find test-re "GPL-2.0+"))))
      (is (not (nil? (re-find test-re "GPL-2.0++"))))
      (is (not (nil? (re-find test-re "GPL-2.0++cpe"))))
      (is (not (nil? (re-find test-re " GPL-2.0+ "))))
      (is (not (nil? (re-find test-re "foo GPL-2.0+"))))
      (is (not (nil? (re-find test-re "GPL-2.0+ bar"))))
      (is (not (nil? (re-find test-re "foo GPL-2.0+ bar"))))
      (is (not (nil? (re-find test-re "foo;GPL-2.0+;bar"))))
      (is (not (nil? (re-find test-re "Apache-1.1 GPL-2.0+ Apache-2.0")))))
    (testing "GPL-2.0+ non-finds"
      (is (nil? (re-find test-re "")))
      (is (nil? (re-find test-re "foobar")))
      (is (nil? (re-find test-re "GPL-2.0")))
      (is (nil? (re-find test-re "fooGPL-2.0+")))
      (is (nil? (re-find test-re "GPL-2.0+bar")))
      (is (nil? (re-find test-re "fooGPL-2.0+bar")))
      (is (nil? (re-find test-re "foo;GPL-2.0+bar")))
      (is (nil? (re-find test-re "fooGPL-2.0+;bar")))))
  (let [test-re (build-re ["Apache-2.0"] {:include-license-refs? true :include-special-forms? true})]
    (testing "matches"
      (is (not (nil? (re-matches test-re "Apache-2.0"))))
      (is (not (nil? (re-matches test-re "aPaChE-2.0"))))
      (is (not (nil? (re-matches test-re "LicenseRef-foo-bar"))))
      (is (not (nil? (re-matches test-re "licenseref-foo-bar"))))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
      (is (not (nil? (re-matches test-re "NONE"))))
      (is (not (nil? (re-matches test-re "none"))))
      (is (not (nil? (re-matches test-re "NOASSERTION"))))
      (is (not (nil? (re-matches test-re "noassertion")))))
    (testing "non matches"
      (is (nil? (re-matches test-re "GPL-2.0")))
      (is (nil? (re-matches test-re "additionref-foo-bar"))))
    (testing "Named capturing groups (via rencg library)"
      (let [m (rencg/re-matches-ncg test-re "Apache-2.0")]
        (is (contains? m "Identifier"))
        (is (not (contains? m "DocumentRef")))
        (is (not (contains? m "LicenseRef")))
        (is (= "Apache-2.0" (get m "Identifier"))))
      (let [m (rencg/re-matches-ncg test-re "licenseref-foo-bar")]  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
        (is (contains? m "Identifier"))
        (is (contains? m "LicenseRef"))
        (is (not (contains? m "DocumentRef")))
        (is (= "licenseref-foo-bar" (get m "Identifier")))  ; Note: NOT canonicalised
        (is (= "foo-bar"            (get m "LicenseRef"))))
      (let [m (rencg/re-matches-ncg test-re "documentref-foo:licenseref-bar")]  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
        (is (contains? m "Identifier"))
        (is (contains? m "LicenseRef"))
        (is (contains? m "DocumentRef"))
        (is (= "documentref-foo:licenseref-bar" (get m "Identifier")))  ; Note: NOT canonicalised
        (is (= "bar"                            (get m "LicenseRef")))
        (is (= "foo"                            (get m "DocumentRef"))))
      (let [m (rencg/re-matches-ncg test-re "NONE")]
        (is (contains? m "Identifier"))
        (is (not (contains? m "LicenseRef")))
        (is (not (contains? m "DocumentRef")))
        (is (= "NONE" (get m "Identifier"))))
      (let [m (rencg/re-matches-ncg test-re "noassertion")]
        (is (contains? m "Identifier"))
        (is (not (contains? m "LicenseRef")))
        (is (not (contains? m "DocumentRef")))
        (is (= "noassertion" (get m "Identifier")))))))  ; Note: NOT canonicalised

(def non-ref-values-that-should-find-and-match
  (concat ["apache-2.0"
           "mIt"
           "NONE"
           "noassertion"]
          (filter #(not (re-matches #".*\+\z" %)) (slic/ids))   ; Strip out the + variants ####TODO: PUT THEM IN A SEPARATE LIST WITH SEPARATE TESTS!!!!
          (sexc/ids)))

(def ref-values-that-should-find-and-match
  ["LicenseRef-foo"
   "licenseref-foo"                                     ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
   "LicenseRef-foo-"                                    ; Cursed but valid
   "LicenseRef-foo."                                    ; Cursed but valid
   "LicenseRef-foo.-.-.-.-.-.-.-.-.-.-.-.-bar"          ; Cursed but valid
   "LicenseRef--"                                       ; Cursed but valid
   "LicenseRef-."                                       ; Cursed but valid
   "LicenseRef-.-.-.-.-.-.-.-."                         ; Cursed but valid
   "LicenseRef-LicenseRef"                              ; Cursed but valid
   "LicenseRef-DocumentRef"                             ; Cursed but valid
   "DocumentRef-foo:LicenseRef-bar"
   "documentref-foo:licenseref-bar"                     ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
   "DocumentRef-foo-:LicenseRef-bar."                   ; Cursed but valid
   "DocumentRef-DocumentRef:LicenseRef-LicenseRef"      ; Cursed but valid
   "DocumentRef-LicenseRef:LicenseRef-DocumentRef"      ; Cursed but valid
   "AdditionRef-foo"
   "additionref-foo"                                    ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
   "AdditionRef-foo-."                                  ; Cursed but valid
   "AdditionRef--"                                      ; Cursed but valid
   "AdditionRef-."                                      ; Cursed but valid
   "AdditionRef-.-.-.-.-.-.-.-."                        ; Cursed but valid
   "AdditionRef-AdditionRef"                            ; Cursed but valid
   "AdditionRef-DocumentRef"                            ; Cursed but valid
   "DocumentRef-foo:AdditionRef-bar"
   "documentref-foo:additionref-bar"                    ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
   "DocumentRef-foo.:AdditionRef-bar-"                  ; Cursed but valid
   "DocumentRef-DocumentRef:AdditionRef-AdditionRef"    ; Cursed but valid
   "DocumentRef-AdditionRef:AdditionRef-DocumentRef"])  ; Cursed but valid

(def values-that-should-find-and-match
  (concat non-ref-values-that-should-find-and-match
          ref-values-that-should-find-and-match
          (map #(str % "-version-2.0") ref-values-that-should-find-and-match)))  ; Extending any valid ref with [\p{Alnum}\-\.]+ will always result in a new, valid ref

(def values-that-should-find-but-not-match
  (concat ["-Apache-2.0-"
           "Apache-1.1 apache-2.0 GPL-2.0"
           ".none"
           "noassertion+"]
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
   "licenseref:foo"                   ; Wrong separator
   "additionaref:foo"                 ; Wrong separator
   "documentRef:foo-Licenseref:bar"   ; Wrong separators
   "documentRef:foo-Additionref:bar"  ; Wrong separators
   "Xnone"
   "noassertionX"])

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
    (is (not (nil? (re-matches (license-ids-re) "DocumentRef-foo:LicenseRef-bar"))))
    (is (not (nil? (re-matches (license-ids-re) "noNe"))))
    (is (not (nil? (re-matches (license-ids-re) "NOASSERtion")))))
  (testing "non-matches"
    (is (nil? (re-matches (license-ids-re) "Apache-200")))
    (is (nil? (re-matches (license-ids-re) " Apache-2.0 ")))
    (is (nil? (re-matches (license-ids-re) "Apache-2.0 GPL-2.0")))
    (is (nil? (re-matches (license-ids-re) "foobar")))
    (is (nil? (re-matches (license-ids-re) "Classpath-exception-2.0")))
    (is (nil? (re-matches (license-ids-re) "AdditionRef-foo")))
    (is (nil? (re-matches (license-ids-re) "DocumentRef-foo:AdditionRef-bar")))
    (is (nil? (re-matches (license-ids-re) " none")))
    (is (nil? (re-matches (license-ids-re) "noassertion ")))))

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
    (is (not (nil? (re-matches (license-ref-re) "licenseref-foo"))))                  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (not (nil? (re-matches (license-ref-re) "DocumentRef-foo:LicenseRef-bar"))))
    (is (not (nil? (re-matches (license-ref-re) "documentRef-foo:Licenseref-bar"))))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (not (nil? (re-matches (license-ref-re) "DocumentRef-foo-bar-2.0:LicenseRef-foo-bar-2.0")))))
  (testing "non-matches"
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
    (is (not (nil? (re-matches (addition-ref-re) "additionref-foo"))))                  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (not (nil? (re-matches (addition-ref-re) "DocumentRef-foo:AdditionRef-bar"))))
    (is (not (nil? (re-matches (addition-ref-re) "documentRef-foo:Additionref-bar"))))  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (not (nil? (re-matches (addition-ref-re) "DocumentRef-foo-bar-2.0:AdditionRef-foo-bar-2.0"))))))
  (testing "non-matches"
    (is (nil? (re-matches (addition-ref-re) "AdditionRef-@%$^")))
    (is (nil? (re-matches (addition-ref-re) "Apache-2.0")))
    (is (nil? (re-matches (addition-ref-re) "Apache-200")))
    (is (nil? (re-matches (addition-ref-re) " Apache-2.0 ")))
    (is (nil? (re-matches (addition-ref-re) "Apache-2.0 GPL-2.0")))
    (is (nil? (re-matches (addition-ref-re) "foobar")))
    (is (nil? (re-matches (addition-ref-re) "Classpath-exception-2.0")))
    (is (nil? (re-matches (addition-ref-re) "LicenseRef-foo")))
    (is (nil? (re-matches (addition-ref-re) "DocumentRef-foo:LicenseRef-bar"))))

; Note: we keep this minimal since the id-seq tests exercise this more thoroughly
(deftest id-seq-matches-tests
  (testing "correct keys are present"
    (is (= #{:start :end :match :type :identifier}                                      (set (keys (first (id-seq-matches "foo Apache-2.0 bar"))))))
    (is (= #{:start :end :match :type :identifier :document-ref :license-ref}           (set (keys (first (id-seq-matches "foo DocumentRef-foo:LicenseRef-foo bar"))))))
    (is (= #{:start :end :match :type :identifier :addition-document-ref :addition-ref} (set (keys (first (id-seq-matches "foo DocumentRef-foo:AdditionRef-foo bar"))))))
    (is (= #{:start :end :match :type :identifier}                                      (set (keys (first (id-seq-matches "foo NONE bar"))))))
    (is (= #{:start :end :match :type :identifier}                                      (set (keys (first (id-seq-matches "foo noassertion bar")))))))
  (testing "synthesised keys have correct values"
    (let [first-match (first (id-seq-matches "foo apache-2.0 bar"))]
      (is (= "Apache-2.0"                     (:identifier            first-match)))
      (is (= :license-id                      (:type                  first-match))))
    (let [first-match (first (id-seq-matches "foo CLASSPATH-EXCEPTION-2.0 bar"))]
      (is (= "Classpath-exception-2.0"        (:identifier            first-match)))
      (is (= :exception-id                    (:type                  first-match))))
    (let [first-match (first (id-seq-matches "foo DocumentRef-foo:LicenseRef-fu bar"))]
      (is (= "DocumentRef-foo:LicenseRef-fu"  (:identifier            first-match)))
      (is (= :license-ref                     (:type                  first-match)))
      (is (= "fu"                             (:license-ref           first-match)))
      (is (= "foo"                            (:document-ref          first-match))))
    (let [first-match (first (id-seq-matches "foo DocumentRef-foo:AdditionRef-fu bar"))]
      (is (= "DocumentRef-foo:AdditionRef-fu" (:identifier            first-match)))
      (is (= :addition-ref                    (:type                  first-match)))
      (is (= "fu"                             (:addition-ref          first-match)))
      (is (= "foo"                            (:addition-document-ref first-match))))
    (let [first-match (first (id-seq-matches "foo none bar"))]
      (is (= "NONE"                           (:identifier            first-match)))
      (is (= :special-form                    (:type                  first-match))))
    (let [first-match (first (id-seq-matches "foo nOaSsErTiOn bar"))]
      (is (= "NOASSERTION"                    (:identifier            first-match)))
      (is (= :special-form                    (:type                  first-match))))))

(deftest id-seq-tests
  (testing "nil, empty, blank, etc."
    (is (nil? (id-seq nil)))
    (is (nil? (id-seq "")))
    (is (nil? (id-seq " ")))
    (is (nil? (id-seq "\t\n\r")))
    (is (nil? (id-seq nil nil)))
    (is (nil? (id-seq nil "")))
    (is (nil? (id-seq nil "foo Apache-2.0 bar"))))
  (testing "non-matches - default re"
    (is (nil? (id-seq "foo")))
    (is (nil? (id-seq "bar")))
    (is (nil? (id-seq "foo bar")))
    (is (nil? (id-seq "Apache 2.0")))
    (is (nil? (id-seq "Apache-2.00")))
    (is (nil? (id-seq "RMIT")))
    (is (nil? (id-seq "blahLicenseRef-fooblah")))
    (is (nil? (id-seq "blahAdditionRef-fooblah")))
    (is (nil? (id-seq "blahNONEblah")))
    (is (nil? (id-seq "blahNOASSERTIONblah"))))
  (testing "single matches - default re"
    (is (= '("Apache-2.0")                      (id-seq "Apache-2.0")))
    (is (= '("Apache-2.0")                      (id-seq "apache-2.0")))
    (is (= '("Apache-2.0")                      (id-seq "APACHE-2.0")))
    (is (= '("Apache-2.0")                      (id-seq "aPaChE-2.0")))
    (is (= '("Apache-2.0")                      (id-seq " Apache-2.0 ")))
    (is (= '("Apache-2.0")                      (id-seq "-Apache-2.0-")))
    (is (= '("Apache-2.0")                      (id-seq "\nApache-2.0\n")))
    (is (= '("Apache-2.0")                      (id-seq "foo Apache-2.0 bar")))
    (is (= '("LicenseRef-foo")                  (id-seq "blah LICENSEREF-foo blahblah")))
    (is (= '("DocumentRef-foo:LicenseRef-bar")  (id-seq "blah documentref-foo:LICENSEREF-bar blahblah")))
    (is (= '("LicenseRef-barblah")              (id-seq "blahDocumentRef-foo:LicenseRef-barblah")))  ; Cursed, but correct
    (is (= '("Classpath-exception-2.0")         (id-seq "foo classpath-exception-2.0 bar")))
    (is (= '("AdditionRef-foo")                 (id-seq "blah ADDITIONREF-foo blahblah")))
    (is (= '("DocumentRef-foo:AdditionRef-bar") (id-seq "blah DOCUMENTREF-foo:additionref-bar blahblah")))
    (is (= '("AdditionRef-barblah")             (id-seq "blahDocumentRef-foo:AdditionRef-barblah")))  ; Cursed, but correct
    (is (= '("NONE")                            (id-seq "blah none blahblah")))
    (is (= '("NOASSERTION")                     (id-seq "blah nOaSsErTiOn blahblah"))))
  (testing "multiple matches - default re"
    (is (= '("Apache-2.0" "MIT")                                                 (id-seq "Apache-2.0 MIT")))
    (is (= '("Apache-2.0" "MIT")                                                 (id-seq "Apache-2.0 OR MIT")))
    (is (= '("Apache-2.0" "MIT")                                                 (id-seq "Apache-2.0 AND MIT")))
    (is (= '("Apache-2.0" "MIT")                                                 (id-seq "foo Apache-2.0 bar MIT blah")))
    (is (= '("GPL-2.0" "Classpath-exception-2.0")                                (id-seq "the gpl-2.0 with classpath-exception-2.0 is old skool")))
    (is (= '("DocumentRef-foo:LicenseRef-foo" "DocumentRef-bar:AdditionRef-bar") (id-seq "the documentRef-foo:licenseRef-foo with documentRef-bar:additionRef-bar is lit fam")))
    (is (= '("NONE" "NOASSERTION")                                               (id-seq "a none was found alongside a noassertion, amongst other things"))))
  (testing "non-default re"
    (is (nil? (id-seq (license-ref-re) "Apache-2.0")))
    (is (nil? (id-seq (license-ref-re) "Classpath-exception-2.0")))
    (is (nil? (id-seq (license-ref-re) "AdditionRef-foo")))
    (is (= '("DocumentRef-foo:LicenseRef-foo") (id-seq (license-ref-re) "the DocumentRef-foo:LicenseRef-foo with DocumentRef-bar:AdditionRef:bar is lit fam")))))
