;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.exceptions-test
  (:require [clojure.test    :refer [deftest testing is]]
            [spdx.test-utils :refer [equivalent-colls?]]
            [spdx.exceptions :refer [version ids listed-id? canonicalise addition-ref?
                                     addition-ref addition-ref-map->string string->addition-ref-map
                                     equivalent? id->info deprecated-id? non-deprecated-ids]]))

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
  (testing "Common ids are present"
    (is (true? (listed-id? "Classpath-exception-2.0")))
    (is (true? (listed-id? "GPL-3.0-linking-exception")))
    (is (true? (listed-id? "Linux-syscall-note"))))
  (testing "Made up ids are not present"
    (is (false? (listed-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))))

(deftest canonicalise-tests
  (testing "Invalid ids/AdditionRefs return nil"
    (is (nil? (canonicalise nil)))
    (is (nil? (canonicalise "")))
    (is (nil? (canonicalise "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "id/AdditionRefs in canonical form"
    (is (= "Classpath-exception-2.0"         (canonicalise "Classpath-exception-2.0")))
    (is (= "Bison-exception-1.24"            (canonicalise "Bison-exception-1.24")))
    (is (= "GCC-exception-2.0"               (canonicalise "GCC-exception-2.0")))
    (is (= "AdditionRef-foo"                 (canonicalise "AdditionRef-foo")))
    (is (= "DocumentRef-foo:AdditionRef-foo" (canonicalise "DocumentRef-foo:AdditionRef-foo"))))
  (testing "id/AdditionRefs not in canonical form"
    (is (= "Classpath-exception-2.0"         (canonicalise "CLASSPATH-EXCEPTION-2.0")))
    (is (= "Bison-exception-1.24"            (canonicalise "bison-exception-1.24")))
    (is (= "GCC-exception-2.0"               (canonicalise "gcc-exception-2.0")))
    (is (= "AdditionRef-foo"                 (canonicalise "additionref-foo")))
    (is (= "DocumentRef-foo:AdditionRef-foo" (canonicalise "DOCUMENTREF-foo:ADDITIONREF-foo")))))

(deftest addition-ref?-tests
  (testing "Invalid AdditionRefs return false"
    (is (false? (addition-ref? nil)))
    (is (false? (addition-ref? "")))
    (is (false? (addition-ref? "INVALID-addition-ref")))
    (is (false? (addition-ref? " AdditionRef-foo")))                  ; Leading whitespace
    (is (false? (addition-ref? "AdditionRef-foo ")))                  ; Trailing whitespace
    (is (false? (addition-ref? "AdditionRef-%#^*")))                  ; Invalid characters in AdditionRef tag
    (is (false? (addition-ref? "AdditionRef-:")))                     ; Invalid characters in AdditionRef tag
    (is (false? (addition-ref? "DocumentRef-%#^*:AdditionRef-bar")))  ; Invalid characters in DocumentRef tag
    (is (false? (addition-ref? "DocumentRef-::AdditionRef-:"))))      ; Invalid characters in DocumentRef and AdditionRef tag
  (testing "Valid AdditionRefs"
    (is (true? (addition-ref? "AdditionRef-foo")))
    (is (true? (addition-ref? "AdditionRef-FOO")))
    (is (true? (addition-ref? "additionref-foo")))                   ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (addition-ref? "ADDITIONREF-foo")))                   ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (addition-ref? "AdditionRef-42")))
    (is (true? (addition-ref? "AdditionRef-foo42")))
    (is (true? (addition-ref? "AdditionRef-42foo")))
    (is (true? (addition-ref? "AdditionRef-foo-v2.1")))
    (is (true? (addition-ref? "AdditionRef--")))                ; Cursed but valid
    (is (true? (addition-ref? "AdditionRef-.")))                ; Cursed but valid
    (is (true? (addition-ref? "AdditionRef-.-.-.-.-.-.-.-.")))  ; Cursed but valid
    (is (true? (addition-ref? "DocumentRef-foo:AdditionRef-bar")))
    (is (true? (addition-ref? "DocumentRef-FOO:AdditionRef-BAR")))
    (is (true? (addition-ref? "documentref-foo:AdditionRef-bar")))   ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (addition-ref? "DOCUMENTREF-foo:AdditionRef-bar")))   ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (addition-ref? "DOCUMENTREF-foo:ADDITIONREF-bar")))   ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (addition-ref? "DocumentRef-42:AdditionRef-42")))
    (is (true? (addition-ref? "DocumentRef-foo42:AdditionRef-bar42")))
    (is (true? (addition-ref? "DocumentRef-42foo:AdditionRef-42bar")))
    (is (true? (addition-ref? "DocumentRef-foo-v2.1:AdditionRef-bar-v3.7")))
    (is (true? (addition-ref? "DocumentRef--:AdditionRef-bar")))                  ; Cursed but valid
    (is (true? (addition-ref? "DocumentRef-.:AdditionRef-bar")))                  ; Cursed but valid
    (is (true? (addition-ref? "DocumentRef----:AdditionRef----")))                ; Cursed but valid
    (is (true? (addition-ref? "DocumentRef-.-.:AdditionRef-.-.")))                ; Cursed but valid
    (is (true? (addition-ref? "DocumentRef-.-.-.-.-.-.-.-.-.:AdditionRef-bar")))  ; Cursed but valid
    (is (true? (addition-ref? "DocumentRef-0123456789-.abcdefgABCDEFG:AdditionRef-0123456789-.abcdefgABCDEFG")))))

(deftest addition-ref-tests
  (testing "Invalid AdditionRefs return nil"
    (is (nil? (addition-ref nil)))
    (is (nil? (addition-ref nil nil)))
    (is (nil? (addition-ref "")))
    (is (nil? (addition-ref "" nil)))
    (is (nil? (addition-ref nil "")))
    (is (nil? (addition-ref "" "")))
    (is (nil? (addition-ref " ")))
    (is (nil? (addition-ref " " nil)))
    (is (nil? (addition-ref nil " ")))
    (is (nil? (addition-ref " " " ")))
    (is (nil? (addition-ref "@foo")))
    (is (nil? (addition-ref "@foo" "bar")))
    (is (nil? (addition-ref "foo" "@bar"))))
  (testing "Valid AdditionRefs"
    (is (addition-ref? (addition-ref "foo")))
    (is (addition-ref? (addition-ref "42")))
    (is (addition-ref? (addition-ref "foo42")))
    (is (addition-ref? (addition-ref "42foo")))
    (is (addition-ref? (addition-ref "foo-v2.1")))
    (is (addition-ref? (addition-ref "-")))                  ; Cursed but valid
    (is (addition-ref? (addition-ref ".")))                  ; Cursed but valid
    (is (addition-ref? (addition-ref ".-.-.-.-.-.-.-.-.")))  ; Cursed but valid
    (is (addition-ref? (addition-ref "foo" "bar")))
    (is (addition-ref? (addition-ref "42" "42")))
    (is (addition-ref? (addition-ref "foo42" "bar42")))
    (is (addition-ref? (addition-ref "42foo" "42bar")))
    (is (addition-ref? (addition-ref "foo-v2.1" "bar-v3.7")))
    (is (addition-ref? (addition-ref "-" "foo")))                  ; Cursed but valid
    (is (addition-ref? (addition-ref "." "foo")))                  ; Cursed but valid
    (is (addition-ref? (addition-ref ".-.-.-.-.-.-.-.-." "foo")))  ; Cursed but valid
    (is (addition-ref? (addition-ref "---" "---")))                ; Cursed but valid
    (is (addition-ref? (addition-ref "..." "...")))))              ; Cursed but valid

(deftest addition-ref-map->string-tests
  (testing "Invalid maps return nil"
    (is (nil? (addition-ref-map->string nil)))
    (is (nil? (addition-ref-map->string {})))
    (is (nil? (addition-ref-map->string {:foo "foo"})))
    (is (nil? (addition-ref-map->string {:addition-document-ref "foo"}))))
  (testing "Valid maps - precise testing"
    (is (= "AdditionRef-foo"                 (addition-ref-map->string {:addition-ref "foo"})))
    (is (= "DocumentRef-foo:AdditionRef-bar" (addition-ref-map->string {:addition-document-ref "foo" :addition-ref "bar"}))))
  (testing "Valid maps - directional testing"
    (is (addition-ref? (addition-ref-map->string {:addition-ref "foo"})))
    (is (addition-ref? (addition-ref-map->string {:addition-ref "42"})))
    (is (addition-ref? (addition-ref-map->string {:addition-ref "foo42"})))
    (is (addition-ref? (addition-ref-map->string {:addition-ref "42foo"})))
    (is (addition-ref? (addition-ref-map->string {:addition-ref "foo-v2.1"})))
    (is (addition-ref? (addition-ref-map->string {:addition-ref "-"})))                  ; Cursed but valid
    (is (addition-ref? (addition-ref-map->string {:addition-ref "."})))                  ; Cursed but valid
    (is (addition-ref? (addition-ref-map->string {:addition-ref ".-.-.-.-.-.-.-.-."})))  ; Cursed but valid
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref "foo"               :addition-ref "bar"})))
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref "42"                :addition-ref "42"})))
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref "foo42"             :addition-ref "bar42"})))
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref "42foo"             :addition-ref "42bar"})))
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref "foo-v2.1"          :addition-ref "bar-v3.7"})))
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref "-"                 :addition-ref "foo"})))    ; Cursed but valid
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref "."                 :addition-ref "foo"})))    ; Cursed but valid
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref ".-.-.-.-.-.-.-.-." :addition-ref "foo"})))    ; Cursed but valid
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref "---"               :addition-ref "---"})))    ; Cursed but valid
    (is (addition-ref? (addition-ref-map->string {:addition-document-ref "..."               :addition-ref "..."})))))  ; Cursed but valid

(deftest string->addition-ref-map-tests
  (testing "Invalid strings return nil"
    (is (nil? (string->addition-ref-map nil)))
    (is (nil? (string->addition-ref-map "")))
    (is (nil? (string->addition-ref-map "INVALID-addition-ref")))
    (is (nil? (string->addition-ref-map " AdditionRef-foo")))
    (is (nil? (string->addition-ref-map "AdditionRef-foo ")))
    (is (nil? (string->addition-ref-map "AdditionRef-%#^*")))
    (is (nil? (string->addition-ref-map "AdditionRef-:")))
    (is (nil? (string->addition-ref-map "DocumentRef-%#^*:AdditionRef-bar")))
    (is (nil? (string->addition-ref-map "DocumentRef-::AdditionRef-:"))))
  (testing "Valid maps - precise testing"
    (is (= {:addition-ref "foo"}                     (string->addition-ref-map "AdditionRef-foo")))
    (is (= {:addition-document-ref "foo" :addition-ref "bar"} (string->addition-ref-map "DocumentRef-foo:AdditionRef-bar"))))
  (testing "Valid maps - directional testing"
    (is (map? (string->addition-ref-map "AdditionRef-foo")))
    (is (map? (string->addition-ref-map "AdditionRef-FOO")))
    (is (map? (string->addition-ref-map "additionref-FOO")))  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (map? (string->addition-ref-map "ADDITIONREF-foo")))  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (map? (string->addition-ref-map "AdditionRef-42")))
    (is (map? (string->addition-ref-map "AdditionRef-foo42")))
    (is (map? (string->addition-ref-map "AdditionRef-42foo")))
    (is (map? (string->addition-ref-map "AdditionRef-foo-v2.1")))
    (is (map? (string->addition-ref-map "AdditionRef--")))                ; Cursed but valid
    (is (map? (string->addition-ref-map "AdditionRef-.")))                ; Cursed but valid
    (is (map? (string->addition-ref-map "AdditionRef-.-.-.-.-.-.-.-.")))  ; Cursed but valid
    (is (map? (string->addition-ref-map "DocumentRef-foo:AdditionRef-bar")))
    (is (map? (string->addition-ref-map "DocumentRef-FOO:AdditionRef-BAR")))
    (is (map? (string->addition-ref-map "documentref-foo:AdditionRef-bar")))  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (map? (string->addition-ref-map "DOCUMENTREF-foo:AdditionRef-bar")))  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (map? (string->addition-ref-map "DocumentRef-42:AdditionRef-42")))
    (is (map? (string->addition-ref-map "DocumentRef-foo42:AdditionRef-bar42")))
    (is (map? (string->addition-ref-map "DocumentRef-42foo:AdditionRef-42bar")))
    (is (map? (string->addition-ref-map "DocumentRef-foo-v2.1:AdditionRef-bar-v3.7")))
    (is (map? (string->addition-ref-map "DocumentRef--:AdditionRef-bar")))                  ; Cursed but valid
    (is (map? (string->addition-ref-map "DocumentRef-.:AdditionRef-bar")))                  ; Cursed but valid
    (is (map? (string->addition-ref-map "DocumentRef----:AdditionRef----")))                ; Cursed but valid
    (is (map? (string->addition-ref-map "DocumentRef-.-.:AdditionRef-.-.")))                ; Cursed but valid
    (is (map? (string->addition-ref-map "DocumentRef-.-.-.-.-.-.-.-.-.:AdditionRef-bar")))  ; Cursed but valid
    (is (map? (string->addition-ref-map "DocumentRef-0123456789-.abcdefgABCDEFG:AdditionRef-0123456789-.abcdefgABCDEFG")))))

(deftest addition-ref-roundtrip-tests
  (testing "Starting with AdditionRef string"
    (let [addition-refs [; Invalid AdditionRef strings that round trip (no other invalid values round trip)
                         nil
                         ; Valid AdditionRef strings
                         "AdditionRef-foo"
                         "AdditionRef-FOO"
                         "AdditionRef-42"
                         "AdditionRef-foo42"
                         "AdditionRef-42foo"
                         "AdditionRef-foo-v2.1"
                         "AdditionRef--"
                         "AdditionRef-."
                         "AdditionRef-.-.-.-.-.-.-.-."
                         "DocumentRef-foo:AdditionRef-bar"
                         "DocumentRef-FOO:AdditionRef-BAR"
                         "DocumentRef-42:AdditionRef-42"
                         "DocumentRef-foo42:AdditionRef-bar42"
                         "DocumentRef-42foo:AdditionRef-42bar"
                         "DocumentRef-foo-v2.1:AdditionRef-bar-v3.7"
                         "DocumentRef--:AdditionRef-bar"
                         "DocumentRef-.:AdditionRef-bar"
                         "DocumentRef----:AdditionRef----"
                         "DocumentRef-.-.:AdditionRef-.-."
                         "DocumentRef-.-.-.-.-.-.-.-.-.:AdditionRef-bar"
                         "DocumentRef-0123456789-.abcdefgABCDEFG:AdditionRef-0123456789-.abcdefgABCDEFG"]]
      (run! #(is (= % (addition-ref-map->string (string->addition-ref-map %))) %) addition-refs)))
  (testing "Starting with AdditionRef map"
    (let [addition-ref-maps [; Invalid AdditionRef maps that round trip (no other invalid values round trip)
                             nil
                             ; Valid AdditionRef maps
                             {:addition-ref "foo"}
                             {:addition-ref "42"}
                             {:addition-ref "foo42"}
                             {:addition-ref "42foo"}
                             {:addition-ref "foo-v2.1"}
                             {:addition-ref "-"}
                             {:addition-ref "."}
                             {:addition-ref ".-.-.-.-.-.-.-.-."}
                             {:addition-document-ref "foo"               :addition-ref "bar"}
                             {:addition-document-ref "42"                :addition-ref "42"}
                             {:addition-document-ref "foo42"             :addition-ref "bar42"}
                             {:addition-document-ref "42foo"             :addition-ref "42bar"}
                             {:addition-document-ref "foo-v2.1"          :addition-ref "bar-v3.7"}
                             {:addition-document-ref "-"                 :addition-ref "foo"}
                             {:addition-document-ref "."                 :addition-ref "foo"}
                             {:addition-document-ref ".-.-.-.-.-.-.-.-." :addition-ref "foo"}
                             {:addition-document-ref "---"               :addition-ref "---"}
                             {:addition-document-ref "..."               :addition-ref "..."}]]
      (run! #(is (= % (string->addition-ref-map (addition-ref-map->string %))) %) addition-ref-maps))))

(deftest equivalent?-tests
  (testing "nil, empty etc."
    (is (true?  (equivalent? nil nil)))
    (is (false? (equivalent? "" nil)))
    (is (false? (equivalent? nil "")))
    (is (false? (equivalent? nil "Classpath-exception-2.0")))
    (is (false? (equivalent? "Classpath-exception-2.0" nil)))
    (is (false? (equivalent? nil "AdditionRef-foo")))
    (is (false? (equivalent? "AdditionRef-foo" nil))))
  (testing "Not an id or AdditionRef"
    (is (false? (equivalent? ""                                                       "Classpath-exception-2.0")))
    (is (false? (equivalent? "Classpath-exception-2.0"                                "")))
    (is (false? (equivalent? "foo"                                                    "foo")))
    (is (false? (equivalent? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent? "Classpath-exception-2.0"                                "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent? "Classpath-exception-0.9"                                "Classpath-exception-0.9")))
    (is (false? (equivalent? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "AdditionRef-foo")))
    (is (false? (equivalent? "AdditionRef:foo"                                        "AdditionRef:foo"))))
  (testing "valid values that are not equivalent"
    (is (false? (equivalent? "Classpath-exception-2.0"         "Classpath-exception-2.0-short")))
    (is (false? (equivalent? "bison-exception-1.24"            "BISON-EXCEPTION-2.2")))
    (is (false? (equivalent? "Classpath-exception-2.0"         "AdditionRef-foo")))
    (is (false? (equivalent? "AdditionRef-FOO"                 "Bison-exception-2.2")))
    (is (false? (equivalent? "AdditionRef-foo"                 "AdditionRef-bar")))
    (is (false? (equivalent? "AdditionRef-foo"                 "DocumentRef-foo:AdditionRef-bar")))
    (is (false? (equivalent? "DocumentRef-bar:AdditionRef-foo" "AdditionRef-foo")))
    (is (false? (equivalent? "DocumentRef-foo:AdditionRef-foo" "DocumentRef-foo:AdditionRef-bar")))
    (is (false? (equivalent? "DocumentRef-foo:AdditionRef-bar" "DocumentRef-bar:AdditionRef-bar"))))
  (testing "valid values that are equivalent"
    (is (true?  (equivalent? "Classpath-exception-2.0"                   "Classpath-exception-2.0")))
    (is (true?  (equivalent? "CLASSPATH-EXCEPTION-2.0"                   "classpath-exception-2.0")))
    (is (true?  (equivalent? "Bison-exception-1.24"                      "bison-exception-1.24")))
    (is (true?  (equivalent? "DocumentRef-FOO:AdditionRef-BAR"           "DocumentRef-foo:AdditionRef-bar")))
    (is (true?  (equivalent? "AdditionRef-foo"                           "AdditionRef-foo")))
    (is (true?  (equivalent? "AdditionRef-foo"                           "AdditionRef-FOO")))
    (is (true?  (equivalent? "ADDITIONREF-foo"                           "additionref-FOO")))  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true?  (equivalent? "DocumentRef-foo:AdditionRef-bar"           "DocumentRef-foo:AdditionRef-bar")))
    (is (true?  (equivalent? "DOCUMENTREF-FOO:ADDITIONREF-BAR"           "documentref-foo:additionref-bar")))  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true?  (equivalent? "DocumentRef-FOO:AdditionRef-bar"           "DocumentRef-foo:AdditionRef-BAR")))
    (is (true?  (equivalent? "DOCUMENTREF-FOO-V2.1:ADDITIONREF-BAR-V3.7" "documentref-foo-v2.1:additionref-bar-v3.7")))))  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2

(deftest id->info-tests
  (testing "Invalid ids return nil"
    (is (nil? (id->info nil)))
    (is (nil? (id->info "")))
    (is (nil? (id->info "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Valid ids are not nil"
    (is (not (nil? (id->info "Classpath-exception-2.0")))))
  (testing "Returned info is a Map"
    (is (instance? java.util.Map (id->info "Classpath-exception-2.0"))))
  (testing "Expected keys are present"
    (is (equivalent-colls? (keys (id->info "Classpath-exception-2.0"))
                           [:id :name :see-also]))
    (is (equivalent-colls? (keys (id->info "Classpath-exception-2.0" {:include-large-text-values? false}))
                           [:id :name :see-also]))
    (is (equivalent-colls? (keys (id->info "Classpath-exception-2.0" {:include-large-text-values? true}))
                           [:id :name :see-also :comment :text :text-template])))
  (testing "Select keys have expected values"
    (let [info (id->info "Classpath-exception-2.0")]
      (is (=           (:name        info) "Classpath exception 2.0"))
      (is (nil?        (:deprecated? info)))
      (is (pos? (count (:see-also    info)))))))

(deftest deprecated-id?-tests
  (testing "Invalid ids return nil"
    (is (false? (deprecated-id? nil)))
    (is (false? (deprecated-id? "")))
    (is (false? (deprecated-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Deprecated ids"
    (is (true? (deprecated-id? "Nokia-Qt-exception-1.1"))))
  (testing "Non-deprecated ids"
    (is (false? (deprecated-id? "Classpath-exception-2.0")))
    (is (false? (deprecated-id? "GPL-3.0-linking-exception")))
    (is (false? (deprecated-id? "LLVM-exception")))
    (is (false? (deprecated-id? "OpenJDK-assembly-exception-1.0")))))

(deftest non-deprecated-ids-tests
  (testing "We have some non-deprecated-ids"
    (is (pos? (count (non-deprecated-ids)))))
  (testing "non-deprecated-ids are a set"
    (is (instance? java.util.Set (non-deprecated-ids)))))

