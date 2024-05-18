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

(ns spdx.exceptions-test
  (:require [clojure.test    :refer [deftest testing is]]
            [spdx.test-utils :refer [equivalent-colls?]]
            [spdx.exceptions :refer [version ids listed-id? addition-ref? id->info deprecated-id? non-deprecated-ids]]))

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
    (is (listed-id? "Classpath-exception-2.0"))
    (is (listed-id? "GPL-3.0-linking-exception"))
    (is (listed-id? "Linux-syscall-note")))
  (testing "Made up ids are not present"
    (is (not (listed-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))))

(deftest addition-ref?-tests
  (testing "Invalid AdditionRefs return nil"
    (is (not (addition-ref? nil)))
    (is (not (addition-ref? "")))
    (is (not (addition-ref? "INVALID-ADDITION-REF"))))
  (testing "Valid AdditionRefs"
    (is (addition-ref? "AdditionRef-foo"))
    (is (addition-ref? "DocumentRef-foo:AdditionRef-bar"))
    (is (addition-ref? "DocumentRef-0123456789-.abcdefgABCDEFG:AdditionRef-0123456789-.abcdefgABCDEFG"))))

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
                           [:id :name :see-also :text :text-html :text-template])))
  (testing "Select keys have expected values"
    (let [info (id->info "Classpath-exception-2.0")]
      (is (=           (:name        info) "Classpath exception 2.0"))
      (is (not         (:deprecated? info)))
      (is (pos? (count (:see-also    info)))))))

(deftest deprecated-id?-tests
  (testing "Invalid ids return nil"
    (is (nil? (deprecated-id? nil)))
    (is (nil? (deprecated-id? "")))
    (is (nil? (deprecated-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
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

