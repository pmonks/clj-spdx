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

(ns spdx.licenses-test
  (:require [clojure.test    :refer [deftest testing is]]
            [spdx.test-utils :refer [equivalent-colls?]]
            [spdx.licenses   :refer [version ids listed-id? license-ref? id->info deprecated-id? non-deprecated-ids osi-approved-id? osi-approved-ids fsf-libre-id? fsf-libre-ids]]))

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
  (testing "Invalid ids return nil"
    (is (not (listed-id? nil)))
    (is (not (listed-id? "")))
    (is (not (listed-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Common ids are present"
    (is (listed-id? "Apache-2.0"))
    (is (listed-id? "GPL-3.0"))
    (is (listed-id? "CC-BY-4.0"))))

(deftest license-ref?-tests
  (testing "Invalid LicenseRefs return nil"
    (is (not (license-ref? nil)))
    (is (not (license-ref? "")))
    (is (not (license-ref? "INVALID-LICENSE-REF"))))
  (testing "Valid LicenseRefs"
    (is (license-ref? "LicenseRef-foo"))
    (is (license-ref? "DocumentRef-foo:LicenseRef-bar"))
    (is (license-ref? "DocumentRef-0123456789-.abcdefgABCDEFG:LicenseRef-0123456789-.abcdefgABCDEFG"))))

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
      (is              (:osi-approved? info))
      (is              (:fsf-libre?    info))
      (is (pos? (count (:cross-refs    info)))))))

(deftest deprecated-id?-tests
  (testing "Invalid ids return nil"
    (is (nil? (deprecated-id? nil)))
    (is (nil? (deprecated-id? "")))
    (is (nil? (deprecated-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Deprecated ids"
    (is (deprecated-id? "GPL-2.0"))
    (is (deprecated-id? "Nunit"))
    (is (deprecated-id? "wxWindows")))
  (testing "Non-deprecated ids"
    (is (not (deprecated-id? "GPL-2.0-only")))
    (is (not (deprecated-id? "GPL-2.0-or-later")))
    (is (not (deprecated-id? "Sendmail")))
    (is (not (deprecated-id? "SSH-OpenSSH")))
    (is (not (deprecated-id? "Latex2e")))
    (is (not (deprecated-id? "MIT")))
    (is (not (deprecated-id? "gnuplot")))
    (is (not (deprecated-id? "OLDAP-2.2.2")))))

(deftest non-deprecated-ids-tests
  (testing "We have some non-deprecated-ids"
    (is (pos? (count (non-deprecated-ids)))))
  (testing "non-deprecated-ids are a set"
    (is (instance? java.util.Set (non-deprecated-ids)))))

(deftest osi-approved-id?-tests
  (testing "Invalid ids return nil"
    (is (nil? (osi-approved-id? nil)))
    (is (nil? (osi-approved-id? "")))
    (is (nil? (osi-approved-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "OSI approved ids"
    (is (osi-approved-id? "Apache-2.0"))
    (is (osi-approved-id? "GPL-3.0"))
    (is (osi-approved-id? "GPL-3.0-only"))
    (is (osi-approved-id? "GPL-3.0-or-later")))
  (testing "Non-OSI approved ids"
    (is (not (osi-approved-id? "BSD-3-Clause-No-Military-License")))
    (is (not (osi-approved-id? "WTFPL")))
    (is (not (osi-approved-id? "CC-BY-SA-4.0")))
    (is (not (osi-approved-id? "BSD-4-Clause")))
    (is (not (osi-approved-id? "JSON")))
    (is (not (osi-approved-id? "X11")))
    (is (not (osi-approved-id? "Beerware")))
    (is (not (osi-approved-id? "Hippocratic-2.1")))))

(deftest osi-approved-ids-tests
  (testing "We have some osi-approved-ids"
    (is (pos? (count (osi-approved-ids)))))
  (testing "osi-approved-ids are a set"
    (is (instance? java.util.Set (osi-approved-ids)))))

(deftest fsf-libre-id?-tests
  (testing "Invalid ids return nil"
    (is (nil? (fsf-libre-id? nil)))
    (is (nil? (fsf-libre-id? "")))
    (is (nil? (fsf-libre-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "FSF Libre ids"
    (is (fsf-libre-id? "Intel"))
    (is (fsf-libre-id? "Unlicense"))
    (is (fsf-libre-id? "Apache-1.0"))
    (is (fsf-libre-id? "CDDL-1.0")))
  (testing "Non-FSF-Libre ids"
    ; Note: the SPDX license list tends to leave this field out rather than populate it with false, hence we don't test with false?
    (is (not (fsf-libre-id? "GPL-1.0")))
    (is (not (fsf-libre-id? "MIT-0")))
    (is (not (fsf-libre-id? "PostgreSQL")))
    (is (not (fsf-libre-id? "Glide")))
    (is (not (fsf-libre-id? "OML")))
    (is (not (fsf-libre-id? "Libpng")))
    (is (not (fsf-libre-id? "MPL-1.0")))
    (is (not (fsf-libre-id? "Xerox")))))

(deftest fsf-libre-ids-ids-tests
  (testing "We have some fsf-libre-ids"
    (is (pos? (count (fsf-libre-ids)))))
  (testing "fsf-libre-ids are a set"
    (is (instance? java.util.Set (fsf-libre-ids)))))

