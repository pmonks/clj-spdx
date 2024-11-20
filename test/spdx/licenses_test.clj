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
  (testing "Invalid ids return false"
    (is (false? (listed-id? nil)))
    (is (false? (listed-id? "")))
    (is (false? (listed-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Common ids are present"
    (is (true? (listed-id? "Apache-2.0")))
    (is (true? (listed-id? "GPL-3.0")))
    (is (true? (listed-id? "CC-BY-4.0")))))

(deftest license-ref?-tests
  (testing "Invalid LicenseRefs return false"
    (is (false? (license-ref? nil)))
    (is (false? (license-ref? "")))
    (is (false? (license-ref? "INVALID-LICENSE-REF")))
    (is (false? (license-ref? " LicenseRef-foo")))                   ; Leading whitespace
    (is (false? (license-ref? "LicenseRef-foo ")))                   ; Trailing whitespace
    (is (false? (license-ref? "LicenseRef-%#^*")))                   ; Invalid characters in LicenseRef tag
    (is (false? (license-ref? "DocumentRef-%#^*:LicenseRef-bar"))))  ; Invalid characters in DocumentRef tag
  (testing "Valid LicenseRefs"
    (is (true? (license-ref? "LicenseRef-foo")))
    (is (true? (license-ref? "DocumentRef-foo:LicenseRef-bar")))
    (is (true? (license-ref? "DocumentRef-0123456789-.abcdefgABCDEFG:LicenseRef-0123456789-.abcdefgABCDEFG")))))

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

