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
            [spdx.licenses    :refer [version ids listed-id? canonicalise-id equivalent-ids? license-ref? license-ref
                                      license-ref-map->string string->license-ref-map equivalent-license-refs?
                                      equivalent? id->info deprecated-id? non-deprecated-ids osi-approved-id?
                                      osi-approved-ids fsf-libre-id? fsf-libre-ids]]
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

(deftest listed-id?-tests
  (testing "Invalid ids return false"
    (is (false? (listed-id? nil)))
    (is (false? (listed-id? "")))
    (is (false? (listed-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Common ids are present"
    (is (true? (listed-id? "Apache-2.0")))
    (is (true? (listed-id? "GPL-3.0")))
    (is (true? (listed-id? "CC-BY-4.0")))))

(deftest canonicalise-id-tests
  (testing "Invalid ids return nil"
    (is (nil? (canonicalise-id nil)))
    (is (nil? (canonicalise-id "")))
    (is (nil? (canonicalise-id "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "id in canonical form"
    (is (= "Apache-2.0" (canonicalise-id "Apache-2.0")))
    (is (= "GPL-3.0"    (canonicalise-id "GPL-3.0")))
    (is (= "CC-BY-4.0"  (canonicalise-id "CC-BY-4.0"))))
  (testing "id not in canonical form"
    (is (= "Apache-2.0" (canonicalise-id "APACHE-2.0")))
    (is (= "GPL-3.0"    (canonicalise-id "gpl-3.0")))
    (is (= "CC-BY-4.0"  (canonicalise-id "cc-by-4.0")))))

(deftest equivalent-ids?-tests
  (testing "nil, empty etc."
    (is (false? (equivalent-ids? nil nil)))
    (is (false? (equivalent-ids? "" nil)))
    (is (false? (equivalent-ids? nil ""))))
  (testing "invalid ids"
    (is (false? (equivalent-ids? "foo" "foo")))
    (is (false? (equivalent-ids? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent-ids? "Apache-2.0"                                             "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent-ids? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "GPL-2.0"))))
  (testing "valid ids that are not equivalent"
    (is (false? (equivalent-ids? "Apache-2.0" "GPL-2.0")))
    (is (false? (equivalent-ids? "cc-by-4.0"  "cc-by-sa-4.0"))))
  (testing "valid ids that are equivalent"
    (is (true? (equivalent-ids? "Apache-2.0"   "Apache-2.0")))
    (is (true? (equivalent-ids? "APACHE-2.0"   "apache-2.0")))
    (is (true? (equivalent-ids? "CC-BY-SA-4.0" "cc-by-sa-4.0")))))

(deftest license-ref?-tests
  (testing "Invalid LicenseRefs return false"
    (is (false? (license-ref? nil)))
    (is (false? (license-ref? "")))
    (is (false? (license-ref? "INVALID-LICENSE-REF")))
    (is (false? (license-ref? " LicenseRef-foo")))                  ; Leading whitespace
    (is (false? (license-ref? "LicenseRef-foo ")))                  ; Trailing whitespace
    (is (false? (license-ref? "licenseref-foo")))                   ; Incorrect case of "LicenseRef"
    (is (false? (license-ref? "LICENSEREF-foo")))                   ; Incorrect case of "LicenseRef"
    (is (false? (license-ref? "LicenseRef-%#^*")))                  ; Invalid characters in LicenseRef tag
    (is (false? (license-ref? "LicenseRef-:")))                     ; Invalid characters in LicenseRef tag
    (is (false? (license-ref? "DocumentRef-%#^*:LicenseRef-bar")))  ; Invalid characters in DocumentRef tag
    (is (false? (license-ref? "DocumentRef-::LicenseRef-:")))       ; Invalid characters in DocumentRef and LicenseRef tag
    (is (false? (license-ref? "documentref-foo:LicenseRef-bar")))   ; Incorrect case of "DocumentRef"
    (is (false? (license-ref? "DOCUMENTREF-foo:LicenseRef-bar"))))  ; Incorrect case of "DocumentRef"
  (testing "Valid LicenseRefs"
    (is (true? (license-ref? "LicenseRef-foo")))
    (is (true? (license-ref? "LicenseRef-FOO")))
    (is (true? (license-ref? "LicenseRef-42")))
    (is (true? (license-ref? "LicenseRef-foo42")))
    (is (true? (license-ref? "LicenseRef-42foo")))
    (is (true? (license-ref? "LicenseRef-foo-v2.1")))
    (is (true? (license-ref? "LicenseRef--")))                ; Cursed but valid
    (is (true? (license-ref? "LicenseRef-.")))                ; Cursed but valid
    (is (true? (license-ref? "LicenseRef-.-.-.-.-.-.-.-.")))  ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-foo:LicenseRef-bar")))
    (is (true? (license-ref? "DocumentRef-FOO:LicenseRef-BAR")))
    (is (true? (license-ref? "DocumentRef-42:LicenseRef-42")))
    (is (true? (license-ref? "DocumentRef-foo42:LicenseRef-bar42")))
    (is (true? (license-ref? "DocumentRef-42foo:LicenseRef-42bar")))
    (is (true? (license-ref? "DocumentRef-foo-v2.1:LicenseRef-bar-v3.7")))
    (is (true? (license-ref? "DocumentRef--:LicenseRef-bar")))                  ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-.:LicenseRef-bar")))                  ; Cursed but valid
    (is (true? (license-ref? "DocumentRef----:LicenseRef----")))                ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-.-.:LicenseRef-.-.")))                ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-.-.-.-.-.-.-.-.-.:LicenseRef-bar")))  ; Cursed but valid
    (is (true? (license-ref? "DocumentRef-0123456789-.abcdefgABCDEFG:LicenseRef-0123456789-.abcdefgABCDEFG")))))

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
    (is (license-ref? (license-ref "-")))                  ; Cursed but valid
    (is (license-ref? (license-ref ".")))                  ; Cursed but valid
    (is (license-ref? (license-ref ".-.-.-.-.-.-.-.-.")))  ; Cursed but valid
    (is (license-ref? (license-ref "foo" "bar")))
    (is (license-ref? (license-ref "42" "42")))
    (is (license-ref? (license-ref "foo42" "bar42")))
    (is (license-ref? (license-ref "42foo" "42bar")))
    (is (license-ref? (license-ref "foo-v2.1" "bar-v3.7")))
    (is (license-ref? (license-ref "-" "foo")))                  ; Cursed but valid
    (is (license-ref? (license-ref "." "foo")))                  ; Cursed but valid
    (is (license-ref? (license-ref ".-.-.-.-.-.-.-.-." "foo")))  ; Cursed but valid
    (is (license-ref? (license-ref "---" "---")))                ; Cursed but valid
    (is (license-ref? (license-ref "..." "...")))))              ; Cursed but valid

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
    (is (license-ref? (license-ref-map->string {:license-ref "-"})))                  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:license-ref "."})))                  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:license-ref ".-.-.-.-.-.-.-.-."})))  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "foo"               :license-ref "bar"})))
    (is (license-ref? (license-ref-map->string {:document-ref "42"                :license-ref "42"})))
    (is (license-ref? (license-ref-map->string {:document-ref "foo42"             :license-ref "bar42"})))
    (is (license-ref? (license-ref-map->string {:document-ref "42foo"             :license-ref "42bar"})))
    (is (license-ref? (license-ref-map->string {:document-ref "foo-v2.1"          :license-ref "bar-v3.7"})))
    (is (license-ref? (license-ref-map->string {:document-ref "-"                 :license-ref "foo"})))                  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "."                 :license-ref "foo"})))                  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref ".-.-.-.-.-.-.-.-." :license-ref "foo"})))  ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "---"               :license-ref "---"})))                ; Cursed but valid
    (is (license-ref? (license-ref-map->string {:document-ref "..."               :license-ref "..."})))))              ; Cursed but valid

(deftest string->license-ref-map-tests
  (testing "Invalid strings return nil"
    (is (nil? (string->license-ref-map nil)))
    (is (nil? (string->license-ref-map "")))
    (is (nil? (string->license-ref-map "INVALID-LICENSE-REF")))
    (is (nil? (string->license-ref-map " LicenseRef-foo")))
    (is (nil? (string->license-ref-map "LicenseRef-foo ")))
    (is (nil? (string->license-ref-map "licenseref-foo")))
    (is (nil? (string->license-ref-map "LICENSEREF-foo")))
    (is (nil? (string->license-ref-map "LicenseRef-%#^*")))
    (is (nil? (string->license-ref-map "LicenseRef-:")))
    (is (nil? (string->license-ref-map "DocumentRef-%#^*:LicenseRef-bar")))
    (is (nil? (string->license-ref-map "DocumentRef-::LicenseRef-:")))
    (is (nil? (string->license-ref-map "documentref-foo:LicenseRef-bar")))
    (is (nil? (string->license-ref-map "DOCUMENTREF-foo:LicenseRef-bar"))))
  (testing "Valid maps - precise testing"
    (is (= {:license-ref "foo"}                     (string->license-ref-map "LicenseRef-foo")))
    (is (= {:document-ref "foo" :license-ref "bar"} (string->license-ref-map "DocumentRef-foo:LicenseRef-bar"))))
  (testing "Valid maps - directional testing"
    (is (map? (string->license-ref-map "LicenseRef-foo")))
    (is (map? (string->license-ref-map "LicenseRef-FOO")))
    (is (map? (string->license-ref-map "LicenseRef-42")))
    (is (map? (string->license-ref-map "LicenseRef-foo42")))
    (is (map? (string->license-ref-map "LicenseRef-42foo")))
    (is (map? (string->license-ref-map "LicenseRef-foo-v2.1")))
    (is (map? (string->license-ref-map "LicenseRef--")))                ; Cursed but valid
    (is (map? (string->license-ref-map "LicenseRef-.")))                ; Cursed but valid
    (is (map? (string->license-ref-map "LicenseRef-.-.-.-.-.-.-.-.")))  ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-foo:LicenseRef-bar")))
    (is (map? (string->license-ref-map "DocumentRef-FOO:LicenseRef-BAR")))
    (is (map? (string->license-ref-map "DocumentRef-42:LicenseRef-42")))
    (is (map? (string->license-ref-map "DocumentRef-foo42:LicenseRef-bar42")))
    (is (map? (string->license-ref-map "DocumentRef-42foo:LicenseRef-42bar")))
    (is (map? (string->license-ref-map "DocumentRef-foo-v2.1:LicenseRef-bar-v3.7")))
    (is (map? (string->license-ref-map "DocumentRef--:LicenseRef-bar")))                  ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-.:LicenseRef-bar")))                  ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef----:LicenseRef----")))                ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-.-.:LicenseRef-.-.")))                ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-.-.-.-.-.-.-.-.-.:LicenseRef-bar")))  ; Cursed but valid
    (is (map? (string->license-ref-map "DocumentRef-0123456789-.abcdefgABCDEFG:LicenseRef-0123456789-.abcdefgABCDEFG")))))

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
  "LicenseRef--"
  "LicenseRef-."
  "LicenseRef-.-.-.-.-.-.-.-."
  "DocumentRef-foo:LicenseRef-bar"
  "DocumentRef-FOO:LicenseRef-BAR"
  "DocumentRef-42:LicenseRef-42"
  "DocumentRef-foo42:LicenseRef-bar42"
  "DocumentRef-42foo:LicenseRef-42bar"
  "DocumentRef-foo-v2.1:LicenseRef-bar-v3.7"
  "DocumentRef--:LicenseRef-bar"
  "DocumentRef-.:LicenseRef-bar"
  "DocumentRef----:LicenseRef----"
  "DocumentRef-.-.:LicenseRef-.-."
  "DocumentRef-.-.-.-.-.-.-.-.-.:LicenseRef-bar"
  "DocumentRef-0123456789-.abcdefgABCDEFG:LicenseRef-0123456789-.abcdefgABCDEFG"])

(deftest parsing-equivalence-tests
  (testing "Equivalence of parsing methods"
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
                            {:license-ref "-"}
                            {:license-ref "."}
                            {:license-ref ".-.-.-.-.-.-.-.-."}
                            {:document-ref "foo"               :license-ref "bar"}
                            {:document-ref "42"                :license-ref "42"}
                            {:document-ref "foo42"             :license-ref "bar42"}
                            {:document-ref "42foo"             :license-ref "42bar"}
                            {:document-ref "foo-v2.1"          :license-ref "bar-v3.7"}
                            {:document-ref "-"                 :license-ref "foo"}
                            {:document-ref "."                 :license-ref "foo"}
                            {:document-ref ".-.-.-.-.-.-.-.-." :license-ref "foo"}
                            {:document-ref "---"               :license-ref "---"}
                            {:document-ref "..."               :license-ref "..."}]]
      (run! #(is (= % (string->license-ref-map (license-ref-map->string %))) %) license-ref-maps))))

(deftest equivalent-license-refs?-tests
  (testing "Invalid LicenseRefs"
    (is (false? (equivalent-license-refs? nil nil)))
    (is (false? (equivalent-license-refs? nil "LicenseRef-foo")))
    (is (false? (equivalent-license-refs? "LicenseRef-foo" nil)))
    (is (false? (equivalent-license-refs? "LICENSEREF-foo" "LICENSEREF-foo")))
    (is (false? (equivalent-license-refs? "LicenseRef:foo" "LicenseRef:foo"))))
  (testing "Valid LicenseRefs - not equivalent"
    (is (false? (equivalent-license-refs? "LicenseRef-foo" "LicenseRef-bar")))
    (is (false? (equivalent-license-refs? "LicenseRef-foo" "DocumentRef-foo:LicenseRef-bar")))
    (is (false? (equivalent-license-refs? "DocumentRef-bar:LicenseRef-foo" "LicenseRef-foo")))
    (is (false? (equivalent-license-refs? "DocumentRef-foo:LicenseRef-foo" "DocumentRef-foo:LicenseRef-bar")))
    (is (false? (equivalent-license-refs? "DocumentRef-foo:LicenseRef-bar" "DocumentRef-bar:LicenseRef-bar"))))
  (testing "Valid and equivalent LicenseRefs"
    (is (true? (equivalent-license-refs? "LicenseRef-foo" "LicenseRef-foo")))
    (is (true? (equivalent-license-refs? "LicenseRef-foo" "LicenseRef-FOO")))
    (is (true? (equivalent-license-refs? "DocumentRef-foo:LicenseRef-bar" "DocumentRef-foo:LicenseRef-bar")))
    (is (true? (equivalent-license-refs? "DocumentRef-FOO:LicenseRef-BAR" "DocumentRef-foo:LicenseRef-bar")))
    (is (true? (equivalent-license-refs? "DocumentRef-FOO:LicenseRef-bar" "DocumentRef-foo:LicenseRef-BAR")))
    (is (true? (equivalent-license-refs? "DocumentRef-FOO-V2.1:LicenseRef-bar-v3.7" "DocumentRef-foo-v2.1:LicenseRef-BAR-V3.7")))))

(deftest equivalent?-tests
  (testing "nil, empty etc."
    (is (false? (equivalent? nil nil)))
    (is (false? (equivalent? "" nil)))
    (is (false? (equivalent? nil ""))))
  (testing "Not an id or LicenseRef"
    (is (false? (equivalent? "foo" "foo")))
    (is (false? (equivalent? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent? "Apache-2.0"                                             "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "LicenseRef-foo"))))
  (testing "valid values that are not equivalent"
    (is (false? (equivalent? "Apache-2.0"     "LicenseRef-foo")))
    (is (false? (equivalent? "LicenseRef-FOO" "gpl-2.0"))))
  (testing "valid values that are equivalent"
    (is (true? (equivalent? "APACHE-2.0"                     "apache-2.0")))
    (is (true? (equivalent? "DocumentRef-FOO:LicenseRef-BAR" "DocumentRef-foo:LicenseRef-bar")))))

(deftest id->info-tests
  (testing "Invalid ids return nil"
    (is (nil? (id->info nil)))
    (is (nil? (id->info "")))
    (is (nil? (id->info "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Valid ids are not nil"
    (is (not (nil? (id->info "Apache-2.0")))))
  (testing "Returned info is a Map"
    (is (instance? java.util.Map (id->info "Apache-2.0"))))
  (testing "Expected keys are present"
    (is (equivalent-colls? (keys (id->info "Apache-2.0"))
                           [:name :cross-refs :id :fsf-libre? :see-also :osi-approved?]))
    (is (equivalent-colls? (keys (id->info "Apache-2.0" {:include-large-text-values? false}))
                           [:name :cross-refs :id :fsf-libre? :see-also :osi-approved?]))
    (is (equivalent-colls? (keys (id->info "Apache-2.0" {:include-large-text-values? true}))
                           [:text-template :text-html :header-template :name :cross-refs :header :header-html :id :comment :fsf-libre? :see-also :osi-approved? :text])))
  (testing "Select keys have expected values"
    (let [info (id->info "Apache-2.0")]
      (is (=           (:name          info) "Apache License 2.0"))
      (is (true?       (:osi-approved? info)))
      (is (true?       (:fsf-libre?    info)))
      (is (pos? (count (:cross-refs    info)))))))

(deftest deprecated-id?-tests
  (testing "Invalid ids return false"
    (is (false? (deprecated-id? nil)))
    (is (false? (deprecated-id? "")))
    (is (false? (deprecated-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Deprecated ids"
    (is (true? (deprecated-id? "GPL-2.0")))
    (is (true? (deprecated-id? "Nunit")))
    (is (true? (deprecated-id? "wxWindows"))))
  (testing "Non-deprecated ids"
    (is (false? (deprecated-id? "GPL-2.0-only")))
    (is (false? (deprecated-id? "GPL-2.0-or-later")))
    (is (false? (deprecated-id? "Sendmail")))
    (is (false? (deprecated-id? "SSH-OpenSSH")))
    (is (false? (deprecated-id? "Latex2e")))
    (is (false? (deprecated-id? "MIT")))
    (is (false? (deprecated-id? "gnuplot")))
    (is (false? (deprecated-id? "OLDAP-2.2.2")))))

(deftest non-deprecated-ids-tests
  (testing "We have some non-deprecated-ids"
    (is (pos? (count (non-deprecated-ids)))))
  (testing "non-deprecated-ids are a set"
    (is (instance? java.util.Set (non-deprecated-ids)))))

(deftest osi-approved-id?-tests
  (testing "Invalid ids return false"
    (is (false? (osi-approved-id? nil)))
    (is (false? (osi-approved-id? "")))
    (is (false? (osi-approved-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "OSI approved ids"
    (is (true? (osi-approved-id? "Apache-2.0")))
    (is (true? (osi-approved-id? "GPL-3.0")))
    (is (true? (osi-approved-id? "GPL-3.0-only")))
    (is (true? (osi-approved-id? "GPL-3.0-or-later"))))
  (testing "Non-OSI approved ids"
    (is (false? (osi-approved-id? "BSD-3-Clause-No-Military-License")))
    (is (false? (osi-approved-id? "WTFPL")))
    (is (false? (osi-approved-id? "CC-BY-SA-4.0")))
    (is (false? (osi-approved-id? "BSD-4-Clause")))
    (is (false? (osi-approved-id? "JSON")))
    (is (false? (osi-approved-id? "X11")))
    (is (false? (osi-approved-id? "Beerware")))
    (is (false? (osi-approved-id? "Hippocratic-2.1")))))

(deftest osi-approved-ids-tests
  (testing "We have some osi-approved-ids"
    (is (pos? (count (osi-approved-ids)))))
  (testing "osi-approved-ids are a set"
    (is (instance? java.util.Set (osi-approved-ids)))))

(deftest fsf-libre-id?-tests
  (testing "Invalid ids return false"
    (is (false? (fsf-libre-id? nil)))
    (is (false? (fsf-libre-id? "")))
    (is (false? (fsf-libre-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "FSF Libre ids"
    (is (true? (fsf-libre-id? "Intel")))
    (is (true? (fsf-libre-id? "Unlicense")))
    (is (true? (fsf-libre-id? "Apache-1.0")))
    (is (true? (fsf-libre-id? "CDDL-1.0"))))
  (testing "Non-FSF-Libre ids"
    ; Note: the SPDX license list tends to leave this field out rather than populate it with false, hence we don't test with false?
    (is (false? (fsf-libre-id? "GPL-1.0")))
    (is (false? (fsf-libre-id? "MIT-0")))
    (is (false? (fsf-libre-id? "PostgreSQL")))
    (is (false? (fsf-libre-id? "Glide")))
    (is (false? (fsf-libre-id? "OML")))
    (is (false? (fsf-libre-id? "Libpng")))
    (is (false? (fsf-libre-id? "MPL-1.0")))
    (is (false? (fsf-libre-id? "Xerox")))))

(deftest fsf-libre-ids-ids-tests
  (testing "We have some fsf-libre-ids"
    (is (pos? (count (fsf-libre-ids)))))
  (testing "fsf-libre-ids are a set"
    (is (instance? java.util.Set (fsf-libre-ids)))))
