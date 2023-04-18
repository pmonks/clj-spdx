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
            [spdx.exceptions :refer [version ids listed-id? id->info non-deprecated-ids init!]]))

; Note: a lot of these tests are very lightweight, since they would otherwise duplicate unit tests that already exist in the underlying Java library

(deftest init!-tests
  (testing "Nil response"
    (is (nil? (init!))))   ; Note: this call is slow (it can take > 1 minute on my laptop), as it forces full initialisation of the underlying Java library
  (testing "Fast on subsequent calls"
    (let [elapsed-time (parse-double (re-find #"[\d\.]+" (with-out-str (time (init!)))))]   ; Note: this regex isn't quite correct, since it will also match things like 1.2.3. (time) doesn't return messages containing that however.
      (is (< elapsed-time 1000.0)))))   ; This call should be a LOT less than 1 second, on basically any computer

(deftest version-tests
  (testing "Version number"
    (is (not (nil? (version))))
    (is (not (nil? (re-matches #"[\d\.]+" (version)))))))

(deftest ids-tests
  (testing "We have some ids"
    (is (pos? (count (ids)))))
  (testing "ids are a set"
    (is (instance? java.util.Set (ids)))))

(deftest non-deprecated-ids-tests
  (testing "We have some non-deprecated-ids"
    (is (pos? (count (non-deprecated-ids)))))
  (testing "non-deprecated-ids are a set"
    (is (instance? java.util.Set (non-deprecated-ids)))))

(deftest listed-id?-tests
  (testing "Common ids are present"
    (is (listed-id? "Classpath-exception-2.0"))
    (is (listed-id? "GPL-3.0-linking-exception"))
    (is (listed-id? "Linux-syscall-note")))
  (testing "Made up ids are not present"
    (is (not (listed-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))))

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
