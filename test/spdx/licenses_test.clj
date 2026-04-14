;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.licenses-test
  (:require [clojure.test     :refer [deftest testing is]]
            [spdx.test-utils  :refer [equivalent-colls?]]
            [spdx.licenses    :refer [version ids listed? canonicalise license-ref? special-form?
                                      license-ref license-ref-map->string string->license-ref-map
                                      equivalent? info deprecated? non-deprecated-ids osi-approved?
                                      osi-approved-ids fsf-libre? fsf-libre-ids]]
            [spdx.expressions :as exp]))

; Note: a lot of these tests are very lightweight, since they would otherwise duplicate unit tests that already exist in the underlying Java library

(deftest version-tests
  (testing "Version number"
    (is (not (nil? (version))))
    (is (not (nil? (re-matches #"[\d\.]+" (version)))))))

(deftest ids-tests
  (testing "We have some ids"
    (is (pos? (count (ids)))))
  (testing "ids are a set"
    (is (instance? java.util.Set (ids)))))

(deftest listed?-tests
  (testing "Invalid ids are not listed"
    (is (false? (listed? nil)))
    (is (false? (listed? "")))
    (is (false? (listed? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "LicenseRefs & special forms are not listed"
    (is (false? (listed? "LicenseRef-foo")))
    (is (false? (listed? "NONE")))
    (is (false? (listed? "NOASSERTION"))))
  (testing "Common ids are listed"
    (is (true? (listed? "Apache-2.0")))
    (is (true? (listed? "GPL-3.0")))
    (is (true? (listed? "CC-BY-4.0"))))
  (testing "ids not in canonical form are listed"
    (is (true? (listed? "APACHE-2.0")))))

(deftest license-ref?-tests
  (testing "Invalid LicenseRefs return false"
    (is (false? (license-ref? nil)))
    (is (false? (license-ref? "")))
    (is (false? (license-ref? "Apache-2.0")))
    (is (false? (license-ref? "NONE")))
    (is (false? (license-ref? "NOASSERTION")))
    (is (false? (license-ref? "INVALID-LICENSE-REF")))
    (is (false? (license-ref? " LicenseRef-foo")))                  ; Leading whitespace
    (is (false? (license-ref? "LicenseRef-foo ")))                  ; Trailing whitespace
    (is (false? (license-ref? "LicenseRef-%#^*")))                  ; Invalid characters in LicenseRef tag
    (is (false? (license-ref? "LicenseRef-:")))                     ; Invalid characters in LicenseRef tag
    (is (false? (license-ref? "DocumentRef-%#^*:LicenseRef-bar")))  ; Invalid characters in DocumentRef tag
    (is (false? (license-ref? "DocumentRef-::LicenseRef-:"))))      ; Invalid characters in DocumentRef and LicenseRef tag
  (testing "Valid LicenseRefs"
    (is (true? (license-ref? "LicenseRef-foo")))
    (is (true? (license-ref? "licenseref-foo")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (license-ref? "LicenseRef-FOO")))
    (is (true? (license-ref? "LICENSEREF-FOO")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (license-ref? "LicenseRef-42")))
    (is (true? (license-ref? "LicenseRef-foo42")))
    (is (true? (license-ref? "LicenseRef-42foo")))
    (is (true? (license-ref? "LicenseRef-foo-v2.1")))
    (is (true? (license-ref? "LicenseRef-LicenseRef")))       ; Cursed but valid
    (is (true? (license-ref? "LicenseRef-DocumentRef")))      ; Cursed but valid
    (is (true? (license-ref? "LicenseRef--")))                ; Cursed but valid
    (is (true? (license-ref? "LicenseRef-.")))                ; Cursed but valid
    (is (true? (license-ref? "LicenseRef-.-.-.-.-.-.-.-.")))  ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-foo:LicenseRef-bar")))
    (is (true? (license-ref? "documentref-foo:licenseref-bar")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (license-ref? "DocumentRef-FOO:LicenseRef-BAR")))
    (is (true? (license-ref? "DOCUMENTREF-FOO:LICENSEREF-BAR")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (license-ref? "DocumentRef-42:LicenseRef-42")))
    (is (true? (license-ref? "DocumentRef-foo42:LicenseRef-bar42")))
    (is (true? (license-ref? "DocumentRef-42foo:LicenseRef-42bar")))
    (is (true? (license-ref? "DocumentRef-foo-v2.1:LicenseRef-bar-v3.7")))
    (is (true? (license-ref? "DocumentRef-DocumentRef:LicenseRef-LicenseRef")))  ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-LicenseRef:LicenseRef-DocumentRef")))  ; Cursed but valid
    (is (true? (license-ref? "DocumentRef--:LicenseRef-bar")))                   ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-.:LicenseRef-bar")))                   ; Cursed but valid
    (is (true? (license-ref? "DocumentRef----:LicenseRef----")))                 ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-.-.:LicenseRef-.-.")))                 ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-.-.-.-.-.-.-.-.-.:LicenseRef-bar")))   ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-0123456789-.abcdefgABCDEFG:LicenseRef-0123456789-.abcdefgABCDEFG")))))

(deftest special-form?-tests
  (testing "Non special forms return false"
    (is (false? (special-form? nil)))
    (is (false? (special-form? "")))
    (is (false? (special-form? "Apache-2.0")))
    (is (false? (special-form? "LicenseRef-foo")))
    (is (false? (special-form? "INVALID-SPECIAL_FORM")))
    (is (false? (special-form? "xNONE")))
    (is (false? (special-form? "NONEx")))
    (is (false? (special-form? ".NONE")))
    (is (false? (special-form? "NONE.")))
    (is (false? (special-form? ".NOASSERTION")))
    (is (false? (special-form? "NOASSERTION.")))
    (is (false? (special-form? " NONE")))          ; Leading whitespace
    (is (false? (special-form? "NONE ")))          ; Trailing whitespace
    (is (false? (special-form? " NOASSERTION")))   ; Leading whitespace
    (is (false? (special-form? "NOASSERTION "))))  ; Trailing whitespace
  (testing "Valid special forms"
    (is (true? (special-form? "NONE")))
    (is (true? (special-form? "NOASSERTION")))
    (is (true? (special-form? "none")))
    (is (true? (special-form? "noassertion")))
    (is (true? (special-form? "nOnE")))
    (is (true? (special-form? "nOaSsErTiOn")))))

(deftest license-ref-tests
  (testing "Invalid LicenseRefs return nil"
    (is (nil? (license-ref nil)))
    (is (nil? (license-ref nil nil)))
    (is (nil? (license-ref "")))
    (is (nil? (license-ref "" nil)))
    (is (nil? (license-ref nil "")))
    (is (nil? (license-ref "" "")))
    (is (nil? (license-ref " ")))
    (is (nil? (license-ref " " nil)))
    (is (nil? (license-ref nil " ")))
    (is (nil? (license-ref " " " ")))
    (is (nil? (license-ref "@foo")))
    (is (nil? (license-ref "@foo" "bar")))
    (is (nil? (license-ref "foo" "@bar"))))
  (testing "Valid LicenseRefs"
    (is (license-ref? (license-ref "foo")))
    (is (license-ref? (license-ref "42")))
    (is (license-ref? (license-ref "foo42")))
    (is (license-ref? (license-ref "42foo")))
    (is (license-ref? (license-ref "foo-v2.1")))
    (is (license-ref? (license-ref "LicenseRef")))         ; Cursed but valid
    (is (license-ref? (license-ref "DocumentRef")))        ; Cursed but valid
    (is (license-ref? (license-ref "-")))                  ; Cursed but valid
    (is (license-ref? (license-ref ".")))                  ; Cursed but valid
    (is (license-ref? (license-ref ".-.-.-.-.-.-.-.-.")))  ; Cursed but valid
    (is (license-ref? (license-ref "Apache-2.0")))         ; Cursed but valid
    (is (license-ref? (license-ref "NONE")))               ; Cursed but valid
    (is (license-ref? (license-ref "NOASSERTION")))        ; Cursed but valid
    (is (license-ref? (license-ref "foo" "bar")))
    (is (license-ref? (license-ref "42" "42")))
    (is (license-ref? (license-ref "foo42" "bar42")))
    (is (license-ref? (license-ref "42foo" "42bar")))
    (is (license-ref? (license-ref "foo-v2.1" "bar-v3.7")))
    (is (license-ref? (license-ref "DocumentRef" "LicenseRef")))  ; Cursed but valid
    (is (license-ref? (license-ref "LicenseRef" "DocumentRef")))  ; Cursed but valid
    (is (license-ref? (license-ref "-" "foo")))                   ; Cursed but valid
    (is (license-ref? (license-ref "." "foo")))                   ; Cursed but valid
    (is (license-ref? (license-ref ".-.-.-.-.-.-.-.-." "foo")))   ; Cursed but valid
    (is (license-ref? (license-ref "---" "---")))                 ; Cursed but valid
    (is (license-ref? (license-ref "..." "...")))                 ; Cursed but valid
    (is (license-ref? (license-ref "Apache-2.0" "foo")))          ; Cursed but valid
    (is (license-ref? (license-ref "NONE" "foo")))                ; Cursed but valid
    (is (license-ref? (license-ref "NOASSERTION" "foo")))))       ; Cursed but valid

(deftest license-ref-map->string-tests
  (testing "Invalid maps return nil"
    (is (nil? (license-ref-map->string nil)))
    (is (nil? (license-ref-map->string {})))
    (is (nil? (license-ref-map->string {:foo "foo"})))
    (is (nil? (license-ref-map->string {:document-ref "foo"}))))
  (testing "Valid maps - precise testing"
    (is (= "LicenseRef-foo"                 (license-ref-map->string {:license-ref "foo"})))
    (is (= "DocumentRef-foo:LicenseRef-bar" (license-ref-map->string {:document-ref "foo" :license-ref "bar"}))))
  (testing "Valid maps - directional testing"
    (is (license-ref? (license-ref-map->string {:license-ref "foo"})))
    (is (license-ref? (license-ref-map->string {:license-ref "42"})))
    (is (license-ref? (license-ref-map->string {:license-ref "foo42"})))
    (is (license-ref? (license-ref-map->string {:license-ref "42foo"})))
    (is (license-ref? (license-ref-map->string {:license-ref "foo-v2.1"})))
    (is (license-ref? (license-ref-map->string {:license-ref "LicenseRef"})))         ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:license-ref "DocumentRef"})))        ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:license-ref "-"})))                  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:license-ref "."})))                  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:license-ref ".-.-.-.-.-.-.-.-."})))  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "foo"               :license-ref "bar"})))
    (is (license-ref? (license-ref-map->string {:document-ref "42"                :license-ref "42"})))
    (is (license-ref? (license-ref-map->string {:document-ref "foo42"             :license-ref "bar42"})))
    (is (license-ref? (license-ref-map->string {:document-ref "42foo"             :license-ref "42bar"})))
    (is (license-ref? (license-ref-map->string {:document-ref "foo-v2.1"          :license-ref "bar-v3.7"})))
    (is (license-ref? (license-ref-map->string {:document-ref "DocumentRef"       :license-ref "LicenseRef"})))   ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "LicenseRef"        :license-ref "DocumentRef"})))  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "-"                 :license-ref "foo"})))          ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "."                 :license-ref "foo"})))          ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref ".-.-.-.-.-.-.-.-." :license-ref "foo"})))          ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "---"               :license-ref "---"})))          ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "..."               :license-ref "..."})))))        ; Cursed but valid

(deftest string->license-ref-map-tests
  (testing "Invalid strings return nil"
    (is (nil? (string->license-ref-map nil)))
    (is (nil? (string->license-ref-map "")))
    (is (nil? (string->license-ref-map "Apache-2.0")))
    (is (nil? (string->license-ref-map "NONE")))
    (is (nil? (string->license-ref-map "NOASSERTION")))
    (is (nil? (string->license-ref-map "INVALID-LICENSE-REF")))
    (is (nil? (string->license-ref-map " LicenseRef-foo")))
    (is (nil? (string->license-ref-map "LicenseRef-foo ")))
    (is (nil? (string->license-ref-map "LicenseRef-%#^*")))
    (is (nil? (string->license-ref-map "LicenseRef-:")))
    (is (nil? (string->license-ref-map "DocumentRef-%#^*:LicenseRef-bar")))
    (is (nil? (string->license-ref-map "DocumentRef-::LicenseRef-:"))))
  (testing "Valid maps - precise testing"
    (is (= {:license-ref "foo"}                     (string->license-ref-map "LicenseRef-foo")))
    (is (= {:document-ref "foo" :license-ref "bar"} (string->license-ref-map "DocumentRef-foo:LicenseRef-bar"))))
  (testing "Valid maps - directional testing"
    (is (map? (string->license-ref-map "LicenseRef-foo")))
    (is (map? (string->license-ref-map "LicenseRef-FOO")))
    (is (map? (string->license-ref-map "licenseref-FOO")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (map? (string->license-ref-map "LICENSEREF-foo")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (map? (string->license-ref-map "LicenseRef-42")))
    (is (map? (string->license-ref-map "LicenseRef-foo42")))
    (is (map? (string->license-ref-map "LicenseRef-42foo")))
    (is (map? (string->license-ref-map "LicenseRef-foo-v2.1")))
    (is (map? (string->license-ref-map "LicenseRef-LicenseRef")))       ; Cursed but valid
    (is (map? (string->license-ref-map "LicenseRef-DocumentRef")))      ; Cursed but valid
    (is (map? (string->license-ref-map "LicenseRef--")))                ; Cursed but valid
    (is (map? (string->license-ref-map "LicenseRef-.")))                ; Cursed but valid
    (is (map? (string->license-ref-map "LicenseRef-.-.-.-.-.-.-.-.")))  ; Cursed but valid
    (is (map? (string->license-ref-map "LicenseRef-Apache-2.0")))       ; Cursed but valid
    (is (map? (string->license-ref-map "LicenseRef-NONE")))             ; Cursed but valid
    (is (map? (string->license-ref-map "LicenseRef-NOASSERTION")))      ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-foo:LicenseRef-bar")))
    (is (map? (string->license-ref-map "DocumentRef-FOO:LicenseRef-BAR")))
    (is (map? (string->license-ref-map "documentref-foo:LicenseRef-bar")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (map? (string->license-ref-map "DOCUMENTREF-foo:LicenseRef-bar")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (map? (string->license-ref-map "DocumentRef-42:LicenseRef-42")))
    (is (map? (string->license-ref-map "DocumentRef-foo42:LicenseRef-bar42")))
    (is (map? (string->license-ref-map "DocumentRef-42foo:LicenseRef-42bar")))
    (is (map? (string->license-ref-map "DocumentRef-foo-v2.1:LicenseRef-bar-v3.7")))
    (is (map? (string->license-ref-map "DocumentRef-DocumentRef:LicenseRef-LicenseRef"))) ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-LicenseRef:LicenseRef-DocumentRef"))) ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef--:LicenseRef-bar")))                  ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-.:LicenseRef-bar")))                  ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef----:LicenseRef----")))                ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-.-.:LicenseRef-.-.")))                ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-.-.-.-.-.-.-.-.-.:LicenseRef-bar")))  ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-Apache-2.0:LicenseRef-bar")))         ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-NONE:LicenseRef-bar")))               ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-NOASSERTION:LicenseRef-bar")))        ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-0123456789-.abcdefgABCDEFG:LicenseRef-0123456789-.abcdefgABCDEFG")))))

(deftest canonicalise-tests
  (testing "Invalid ids/LicenseRefs/special forms return nil"
    (is (nil? (canonicalise nil)))
    (is (nil? (canonicalise "")))
    (is (nil? (canonicalise "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (nil? (canonicalise "LicenseRef:foo")))
    (is (nil? (canonicalise "-None")))
    (is (nil? (canonicalise "noassertion+"))))
  (testing "id/LicenseRef/special form in canonical form"
    (is (= "Apache-2.0"                     (canonicalise "Apache-2.0")))
    (is (= "GPL-3.0"                        (canonicalise "GPL-3.0")))
    (is (= "CC-BY-4.0"                      (canonicalise "CC-BY-4.0")))
    (is (= "LicenseRef-foo"                 (canonicalise "LicenseRef-foo")))
    (is (= "DocumentRef-foo:LicenseRef-foo" (canonicalise "DocumentRef-foo:LicenseRef-foo")))
    (is (= "NONE"                           (canonicalise "NONE")))
    (is (= "NOASSERTION"                    (canonicalise "NOASSERTION"))))
  (testing "id/LicenseRef/special form not in canonical form"
    (is (= "Apache-2.0"                     (canonicalise "APACHE-2.0")))
    (is (= "GPL-3.0"                        (canonicalise "gpl-3.0")))
    (is (= "CC-BY-4.0"                      (canonicalise "cc-by-4.0")))
    (is (= "LicenseRef-foo"                 (canonicalise "licenseref-foo")))
    (is (= "DocumentRef-foo:LicenseRef-foo" (canonicalise "DOCUMENTREF-foo:LICENSEREF-foo")))
    (is (= "NONE"                           (canonicalise "nOnE")))
    (is (= "NOASSERTION"                    (canonicalise "nOaSsErTiOn")))))

(def roundtrip-license-refs [
  ; Invalid LicenseRef strings that round trip (no other invalid values round trip)
  nil
  ; Valid LicenseRef strings
  "LicenseRef-foo"
  "LicenseRef-FOO"
  "LicenseRef-42"
  "LicenseRef-foo42"
  "LicenseRef-42foo"
  "LicenseRef-foo-v2.1"
  "LicenseRef-LicenseRef"
  "LicenseRef-DocumentRef"
  "LicenseRef--"
  "LicenseRef-."
  "LicenseRef-.-.-.-.-.-.-.-."
  "LicenseRef-Apache-2.0"
  "LicenseRef-NONE"
  "LicenseRef-NOASSERTION"
  "DocumentRef-foo:LicenseRef-bar"
  "DocumentRef-FOO:LicenseRef-BAR"
  "DocumentRef-42:LicenseRef-42"
  "DocumentRef-foo42:LicenseRef-bar42"
  "DocumentRef-42foo:LicenseRef-42bar"
  "DocumentRef-foo-v2.1:LicenseRef-bar-v3.7"
  "DocumentRef-DocumentRef:LicenseRef-LicenseRef"
  "DocumentRef-LicenseRef:LicenseRef-DocumentRef"
  "DocumentRef--:LicenseRef-bar"
  "DocumentRef-.:LicenseRef-bar"
  "DocumentRef----:LicenseRef----"
  "DocumentRef-.-.:LicenseRef-.-."
  "DocumentRef-.-.-.-.-.-.-.-.-.:LicenseRef-bar"
  "DocumentRef-Apache-2.0:LicenseRef-bar"
  "DocumentRef-NONE:LicenseRef-bar"
  "DocumentRef-NOASSERTION:LicenseRef-bar"
  "DocumentRef-0123456789-.abcdefgABCDEFG:LicenseRef-0123456789-.abcdefgABCDEFG"])

(deftest parsing-equivalence-tests
  (testing "Equivalence of parsing functions"
    (run! #(is (= (string->license-ref-map %) (exp/parse %)) %) roundtrip-license-refs)))

(deftest license-ref-roundtrip-tests
  (testing "Starting with LicenseRef string"
    (run! #(is (= % (license-ref-map->string (string->license-ref-map %))) %) roundtrip-license-refs))
  (testing "Starting with LicenseRef map"
    (let [license-ref-maps [; Invalid LicenseRef maps that round trip (no other invalid values round trip)
                            nil
                            ; Valid LicenseRef maps
                            {:license-ref "foo"}
                            {:license-ref "42"}
                            {:license-ref "foo42"}
                            {:license-ref "42foo"}
                            {:license-ref "foo-v2.1"}
                            {:license-ref "LicenseRef"}
                            {:license-ref "DocumentRef"}
                            {:license-ref "-"}
                            {:license-ref "."}
                            {:license-ref ".-.-.-.-.-.-.-.-."}
                            {:license-ref "Apache-2.0"}
                            {:license-ref "NONE"}
                            {:license-ref "NOASSERTION"}
                            {:document-ref "foo"               :license-ref "bar"}
                            {:document-ref "42"                :license-ref "42"}
                            {:document-ref "foo42"             :license-ref "bar42"}
                            {:document-ref "42foo"             :license-ref "42bar"}
                            {:document-ref "foo-v2.1"          :license-ref "bar-v3.7"}
                            {:document-ref "DocumentRef"       :license-ref "LicenseRef"}
                            {:document-ref "LicenseRef"        :license-ref "DocumentRef"}
                            {:document-ref "-"                 :license-ref "foo"}
                            {:document-ref "."                 :license-ref "foo"}
                            {:document-ref ".-.-.-.-.-.-.-.-." :license-ref "foo"}
                            {:document-ref "---"               :license-ref "---"}
                            {:document-ref "..."               :license-ref "..."}
                            {:document-ref "Apache-2.0"        :license-ref "bar"}
                            {:document-ref "NONE"              :license-ref "bar"}
                            {:document-ref "NOASSERTION"       :license-ref "bar"}]]
      (run! #(is (= % (string->license-ref-map (license-ref-map->string %))) %) license-ref-maps))))

(deftest equivalent?-tests
  (testing "nil, empty etc."
    (is (true?  (equivalent? nil nil)))
    (is (false? (equivalent? "" nil)))
    (is (false? (equivalent? nil "")))
    (is (false? (equivalent? nil "Apache-2.0")))
    (is (false? (equivalent? "Apache-2.0" nil)))
    (is (false? (equivalent? nil "LicenseRef-foo")))
    (is (false? (equivalent? "LicenseRef-foo" nil))))
  (testing "Not an id, LicenseRef, or special form"
    (is (false? (equivalent? ""                                                       "Apache-2.0")))
    (is (false? (equivalent? "Apache-2.0"                                             "")))
    (is (false? (equivalent? "foo"                                                    "foo")))
    (is (false? (equivalent? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent? "Apache-2.0"                                             "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent? "Apache-0.9"                                             "Apache-0.9")))
    (is (false? (equivalent? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "LicenseRef-foo")))
    (is (false? (equivalent? "LicenseRef:foo"                                         "LicenseRef:foo")))
    (is (false? (equivalent? "foo"                                                    "NONE")))
    (is (false? (equivalent? "NOASSERTION"                                            "foo"))))
  (testing "valid values that are not equivalent"
    (is (false? (equivalent? "Apache-2.0"                     "GPL-2.0")))
    (is (false? (equivalent? "cc-by-4.0"                      "cc-by-sa-4.0")))
    (is (false? (equivalent? "Apache-2.0"                     "LicenseRef-foo")))
    (is (false? (equivalent? "Apache-2.0"                     "NONE")))
    (is (false? (equivalent? "Apache-2.0"                     "noassertion")))
    (is (false? (equivalent? "LicenseRef-FOO"                 "gpl-2.0")))
    (is (false? (equivalent? "LicenseRef-foo"                 "LicenseRef-bar")))
    (is (false? (equivalent? "LicenseRef-foo"                 "DocumentRef-foo:LicenseRef-bar")))
    (is (false? (equivalent? "DocumentRef-bar:LicenseRef-foo" "LicenseRef-foo")))
    (is (false? (equivalent? "DocumentRef-foo:LicenseRef-foo" "DocumentRef-foo:LicenseRef-bar")))
    (is (false? (equivalent? "DocumentRef-foo:LicenseRef-bar" "DocumentRef-bar:LicenseRef-bar")))
    (is (false? (equivalent? "none"                           "noassertion")))
    (is (false? (equivalent? "none"                           "LicenseRef-foo"))))
  (testing "valid values that are equivalent"
    (is (true?  (equivalent? "Apache-2.0"                                    "Apache-2.0")))
    (is (true?  (equivalent? "APACHE-2.0"                                    "apache-2.0")))
    (is (true?  (equivalent? "CC-BY-SA-4.0"                                  "cc-by-sa-4.0")))
    (is (true?  (equivalent? "DocumentRef-FOO:LicenseRef-BAR"                "DocumentRef-foo:LicenseRef-bar")))
    (is (true?  (equivalent? "LicenseRef-foo"                                "LicenseRef-foo")))
    (is (true?  (equivalent? "LicenseRef-foo"                                "LicenseRef-FOO")))
    (is (true?  (equivalent? "LicenseRef-LicenseRef"                         "LicenseRef-licenseref")))
    (is (true?  (equivalent? "LicenseRef-DocumentRef"                        "LicenseRef-documentref")))
    (is (true?  (equivalent? "LICENSEREF-foo"                                "licenseref-FOO")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true?  (equivalent? "DocumentRef-foo:LicenseRef-bar"                "DocumentRef-foo:LicenseRef-bar")))
    (is (true?  (equivalent? "DOCUMENTREF-FOO:LICENSEREF-BAR"                "documentref-foo:licenseref-bar")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true?  (equivalent? "DocumentRef-FOO:LicenseRef-bar"                "DocumentRef-foo:LicenseRef-BAR")))
    (is (true?  (equivalent? "DocumentRef-DocumentRef:LicenseRef-LicenseRef" "DocumentRef-documentref:LicenseRef-licenseref")))
    (is (true?  (equivalent? "DocumentRef-LicenseRef:LicenseRef-DocumentRef" "DocumentRef-licenseref:LicenseRef-documentref")))
    (is (true?  (equivalent? "DOCUMENTREF-FOO-V2.1:LICENSEREF-BAR-V3.7"      "documentref-foo-v2.1:licenseref-bar-v3.7")))  ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true?  (equivalent? "NONE"                                          "none")))
    (is (true?  (equivalent? "noassertion"                                   "NOASSERTION")))))

(deftest info-tests
  (testing "Invalid ids, LicenseRefs and special forms return nil"
    (is (nil? (info nil)))
    (is (nil? (info "")))
    (is (nil? (info "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (nil? (info "LicenseRef-foo")))
    (is (nil? (info "NONE")))
    (is (nil? (info "noassertion"))))
  (testing "Valid ids are not nil"
    (is (not (nil? (info "Apache-2.0")))))
  (testing "Returned info is a Map"
    (is (instance? java.util.Map (info "Apache-2.0"))))
  (testing "Expected keys are present"
    (is (equivalent-colls? (keys (info "Apache-2.0"))
                           [:name :id :fsf-libre? :see-also :osi-approved?]))
    (is (equivalent-colls? (keys (info "Apache-2.0" {:include-large-text-values? false}))
                           [:name :id :fsf-libre? :see-also :osi-approved?]))
    (is (equivalent-colls? (keys (info "Apache-2.0" {:include-large-text-values? true}))
                           [:name :id :fsf-libre? :see-also :osi-approved? :text :text-template :header :comment])))
  (testing "Select keys have expected values"
    (let [info (info "Apache-2.0")]
      (is (=           (:name          info) "Apache License 2.0"))
      (is (true?       (:osi-approved? info)))
      (is (true?       (:fsf-libre?    info)))
      (is (pos? (count (:see-also      info))))))
  (testing "ids not in canonical form"
    (let [info (info "apache-2.0")]
      (is (=           (:id            info) "Apache-2.0"))
      (is (=           (:name          info) "Apache License 2.0"))
      (is (true?       (:osi-approved? info)))
      (is (true?       (:fsf-libre?    info)))
      (is (pos? (count (:see-also      info)))))))

(deftest deprecated?-tests
  (testing "Invalid ids, LicenseRefs and special forms return false"
    (is (false? (deprecated? nil)))
    (is (false? (deprecated? "")))
    (is (false? (deprecated? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (deprecated? "LicenseRef-foo")))
    (is (false? (deprecated? "NONE")))
    (is (false? (deprecated? "noassertion"))))
  (testing "Deprecated ids"
    (is (true? (deprecated? "GPL-2.0")))
    (is (true? (deprecated? "Nunit")))
    (is (true? (deprecated? "wxWindows"))))
  (testing "Non-deprecated ids"
    (is (false? (deprecated? "GPL-2.0-only")))
    (is (false? (deprecated? "GPL-2.0-or-later")))
    (is (false? (deprecated? "Sendmail")))
    (is (false? (deprecated? "SSH-OpenSSH")))
    (is (false? (deprecated? "Latex2e")))
    (is (false? (deprecated? "MIT")))
    (is (false? (deprecated? "gnuplot")))
    (is (false? (deprecated? "OLDAP-2.2.2"))))
  (testing "ids not in canonical form"
    (is (true?  (deprecated? "gpl-2.0")))
    (is (false? (deprecated? "mit")))))

(deftest non-deprecated-ids-tests
  (testing "We have some non-deprecated-ids"
    (is (pos? (count (non-deprecated-ids)))))
  (testing "non-deprecated-ids are a set"
    (is (instance? java.util.Set (non-deprecated-ids)))))

(deftest osi-approved?-tests
  (testing "Invalid ids, LicenseRefs and special forms return false"
    (is (false? (osi-approved? nil)))
    (is (false? (osi-approved? "")))
    (is (false? (osi-approved? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (osi-approved? "LicenseRef-foo")))
    (is (false? (osi-approved? "NONE")))
    (is (false? (osi-approved? "noassertion"))))
  (testing "OSI approved ids"
    (is (true? (osi-approved? "Apache-2.0")))
    (is (true? (osi-approved? "GPL-3.0")))
    (is (true? (osi-approved? "GPL-3.0-only")))
    (is (true? (osi-approved? "GPL-3.0-or-later"))))
  (testing "Non-OSI approved ids"
    (is (false? (osi-approved? "BSD-3-Clause-No-Military-License")))
    (is (false? (osi-approved? "WTFPL")))
    (is (false? (osi-approved? "CC-BY-SA-4.0")))
    (is (false? (osi-approved? "BSD-4-Clause")))
    (is (false? (osi-approved? "JSON")))
    (is (false? (osi-approved? "X11")))
    (is (false? (osi-approved? "Beerware")))
    (is (false? (osi-approved? "Hippocratic-2.1"))))
  (testing "ids not in canonical form"
    (is (true?  (osi-approved? "gpl-3.0")))
    (is (false? (osi-approved? "json")))))

(deftest osi-approved-ids-tests
  (testing "We have some osi-approved-ids"
    (is (pos? (count (osi-approved-ids)))))
  (testing "osi-approved-ids are a set"
    (is (instance? java.util.Set (osi-approved-ids)))))

(deftest fsf-libre?-tests
  (testing "Invalid ids, LicenseRefs and special forms return false"
    (is (false? (fsf-libre? nil)))
    (is (false? (fsf-libre? "")))
    (is (false? (fsf-libre? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (fsf-libre? "LicenseRef-foo")))
    (is (false? (fsf-libre? "NONE")))
    (is (false? (fsf-libre? "noassertion"))))
  (testing "FSF Libre ids"
    (is (true? (fsf-libre? "Intel")))
    (is (true? (fsf-libre? "Unlicense")))
    (is (true? (fsf-libre? "Apache-1.0")))
    (is (true? (fsf-libre? "CDDL-1.0"))))
  (testing "Non-FSF-Libre ids"
    ; Note: the SPDX license list tends to leave this field out rather than populate it with false, hence we don't test with false?
    (is (false? (fsf-libre? "GPL-1.0")))
    (is (false? (fsf-libre? "MIT-0")))
    (is (false? (fsf-libre? "PostgreSQL")))
    (is (false? (fsf-libre? "Glide")))
    (is (false? (fsf-libre? "OML")))
    (is (false? (fsf-libre? "Libpng")))
    (is (false? (fsf-libre? "MPL-1.0")))
    (is (false? (fsf-libre? "Xerox"))))
  (testing "ids not in canonical form"
    (is (true?  (fsf-libre? "cddl-1.0")))
    (is (false? (fsf-libre? "oml")))))

(deftest fsf-libre-ids-ids-tests
  (testing "We have some fsf-libre-ids"
    (is (pos? (count (fsf-libre-ids)))))
  (testing "fsf-libre-ids are a set"
    (is (instance? java.util.Set (fsf-libre-ids)))))
