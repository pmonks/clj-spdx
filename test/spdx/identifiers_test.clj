;
; Copyright © 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.identifiers-test
  (:require [clojure.test     :refer [deftest testing is]]
            [spdx.test-utils  :refer [equivalent-colls?]]
            [spdx.identifiers :refer [version ids id-type listed-id? canonicalise equivalent?
                                      id->info deprecated-id? non-deprecated-ids]]))

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

(deftest id-type-tests
  (testing "Invalid ids return nil"
    (is (nil? (id-type nil)))
    (is (nil? (id-type "")))
    (is (nil? (id-type "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Valid identifiers"
    (is (= :license-id   (id-type "Apache-2.0")))
    (is (= :license-id   (id-type "apache-2.0")))
    (is (= :license-id   (id-type "APACHE-2.0")))
    (is (= :exception-id (id-type "Classpath-exception-2.0")))
    (is (= :exception-id (id-type "classpath-exception-2.0")))
    (is (= :exception-id (id-type "CLASSPATH-EXCEPTION-2.0")))
    (is (= :license-ref  (id-type "LicenseRef-foo")))
    (is (= :license-ref  (id-type "DocumentRef-foo:LicenseRef-foo")))
    (is (= :addition-ref (id-type "AdditionRef-foo")))
    (is (= :addition-ref (id-type "DocumentRef-foo:AdditionRef-foo")))))

(deftest listed-id?-tests
  (testing "Invalid ids return false"
    (is (false? (listed-id? nil)))
    (is (false? (listed-id? "")))
    (is (false? (listed-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Common ids are present"
    (is (true? (listed-id? "Apache-2.0")))
    (is (true? (listed-id? "GPL-3.0")))
    (is (true? (listed-id? "Classpath-exception-2.0")))
    (is (true? (listed-id? "CC-BY-4.0")))))

(deftest canonicalise-tests
  (testing "Invalid values return nil"
    (is (nil? (canonicalise nil)))
    (is (nil? (canonicalise "")))
    (is (nil? (canonicalise "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "id/ref in canonical form"
    (is (= "Apache-2.0"                      (canonicalise "Apache-2.0")))
    (is (= "GPL-3.0"                         (canonicalise "GPL-3.0")))
    (is (= "Classpath-exception-2.0"         (canonicalise "Classpath-exception-2.0")))
    (is (= "CC-BY-4.0"                       (canonicalise "CC-BY-4.0")))
    (is (= "LicenseRef-foo"                  (canonicalise "LicenseRef-foo")))
    (is (= "DocumentRef-foo:LicenseRef-foo"  (canonicalise "DocumentRef-foo:LicenseRef-foo")))
    (is (= "AdditionRef-foo"                 (canonicalise "AdditionRef-foo")))
    (is (= "DocumentRef-foo:AdditionRef-foo" (canonicalise "DocumentRef-foo:AdditionRef-foo"))))
  (testing "id/ref not in canonical form"
    (is (= "Apache-2.0"              (canonicalise "APACHE-2.0")))
    (is (= "GPL-3.0"                 (canonicalise "gpl-3.0")))
    (is (= "Classpath-exception-2.0" (canonicalise "classpath-EXCEPTION-2.0")))
    (is (= "CC-BY-4.0"               (canonicalise "cc-by-4.0")))
    (is (= "LicenseRef-foo"                  (canonicalise "licenseref-foo")))
    (is (= "DocumentRef-FOO:LicenseRef-FOO"  (canonicalise "DOCUMENTREF-FOO:LICENSEREF-FOO")))
    (is (= "AdditionRef-FOO"                 (canonicalise "ADDITIONREF-FOO")))
    (is (= "DocumentRef-foo:AdditionRef-foo" (canonicalise "documentref-foo:additionref-foo")))))

(deftest equivalent?-tests
  (testing "nil, empty etc."
    (is (true?  (equivalent? nil nil)))
    (is (false? (equivalent? "" nil)))
    (is (false? (equivalent? nil ""))))
  (testing "Not an id or Ref"
    (is (false? (equivalent? "foo"                                                    "foo")))
    (is (false? (equivalent? "Apache-0.9"                                             "Apache-0.9")))
    (is (false? (equivalent? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent? "Apache-2.0"                                             "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent? "Classpath-exception-2.0"                                "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL")))
    (is (false? (equivalent? "Classpath-exception-0.9"                                "Classpath-exception-0.9")))
    (is (false? (equivalent? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "LicenseRef-foo")))
    (is (false? (equivalent? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL" "AdditionRef-foo"))))
  (testing "valid values that are not equivalent"
    (is (false? (equivalent? "Apache-2.0"                      "Classpath-exception-2.0")))
    (is (false? (equivalent? "Apache-2.0"                      "LicenseRef-foo")))
    (is (false? (equivalent? "CLASSPATH-EXCEPTION-2.0"         "ECOS-EXCEPTION-2.0")))
    (is (false? (equivalent? "LicenseRef-FOO"                  "gpl-2.0")))
    (is (false? (equivalent? "AdditionRef-foo"                 "mit")))
    (is (false? (equivalent? "LicenseRef-FOO"                  "AdditionRef-foo")))
    (is (false? (equivalent? "DocumentRef-foo:LicenseRef-foo"  "LicenseRef-foo")))
    (is (false? (equivalent? "DocumentRef-foo:AdditionRef-foo" "AdditionRef-foo"))))
  (testing "valid values that are equivalent"
    (is (true? (equivalent? "APACHE-2.0"                      "apache-2.0")))
    (is (true? (equivalent? "CLASSPATH-EXCEPTION-2.0"         "classpath-exception-2.0")))
    (is (true? (equivalent? "licenseref-FOO"                  "LICENSEREF-foo")))                     ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (equivalent? "DocumentRef-FOO:LicenseRef-BAR"  "documentRef-foo:licenseRef-bar")))     ; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (equivalent? "additionref-FOO"                 "ADDITIONREF-foo")))                    ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
    (is (true? (equivalent? "DocumentRef-FOO:AdditionRef-BAR" "documentRef-foo:additionRef-bar")))))  ; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2

(deftest id->info-tests
  (testing "Invalid ids return nil"
    (is (nil? (id->info nil)))
    (is (nil? (id->info "")))
    (is (nil? (id->info "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Valid ids are not nil"
    (is (not (nil? (id->info "Apache-2.0"))))
    (is (not (nil? (id->info "eCos-exception-2.0")))))
  (testing "Valid ids are canonicalised before retrieval"
    (is (not (nil? (id->info "apache-2.0"))))
    (is (not (nil? (id->info "ECOS-EXCEPTION-2.0")))))
  (testing "Returned info is a Map"
    (is (instance? java.util.Map (id->info "Apache-2.0")))
    (is (instance? java.util.Map (id->info "eCos-exception-2.0"))))
  (testing "Expected keys are present"
    (is (equivalent-colls? (keys (id->info "Apache-2.0"))
                           [:id :type :name :see-also :fsf-libre? :osi-approved?]))
    (is (equivalent-colls? (keys (id->info "Apache-2.0" {:include-large-text-values? false}))
                           [:id :type :name :see-also :fsf-libre? :osi-approved?]))
    (is (equivalent-colls? (keys (id->info "Apache-2.0" {:include-large-text-values? true}))
                           [:id :type :name :see-also :fsf-libre? :osi-approved? :text :text-template :header :comment]))
    (is (equivalent-colls? (keys (id->info "eCos-exception-2.0"))
                           [:id :type :name :see-also]))
    (is (equivalent-colls? (keys (id->info "eCos-exception-2.0" {:include-large-text-values? false}))
                           [:id :type :name :see-also]))
    (is (equivalent-colls? (keys (id->info "eCos-exception-2.0" {:include-large-text-values? true}))
                           [:id :type :name :see-also :comment :text :text-template])))
  (testing "Select keys have expected values"
    (let [info (id->info "Apache-2.0")]
      (is (=           (:id            info) "Apache-2.0"))
      (is (=           (:type          info) :license-id))
      (is (=           (:name          info) "Apache License 2.0"))
      (is (pos? (count (:see-also      info))))
      (is (true?       (:osi-approved? info)))
      (is (true?       (:fsf-libre?    info)))
      (is (nil?        (:deprecated?   info))))
    (let [info (id->info "Classpath-exception-2.0")]
      (is (=           (:id          info) "Classpath-exception-2.0"))
      (is (=           (:type        info) :exception-id))
      (is (=           (:name        info) "Classpath exception 2.0"))
      (is (pos? (count (:see-also    info)))
      (is (nil?        (:deprecated? info)))))))

(deftest deprecated-id?-tests
  (testing "Invalid ids return false"
    (is (false? (deprecated-id? nil)))
    (is (false? (deprecated-id? "")))
    (is (false? (deprecated-id? "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Deprecated ids"
    (is (true? (deprecated-id? "GPL-2.0")))
    (is (true? (deprecated-id? "Nunit")))
    (is (true? (deprecated-id? "wxWindows")))
    (is (true? (deprecated-id? "Nokia-Qt-exception-1.1"))))
  (testing "Non-deprecated ids"
    (is (false? (deprecated-id? "GPL-2.0-only")))
    (is (false? (deprecated-id? "GPL-2.0-or-later")))
    (is (false? (deprecated-id? "Sendmail")))
    (is (false? (deprecated-id? "SSH-OpenSSH")))
    (is (false? (deprecated-id? "Latex2e")))
    (is (false? (deprecated-id? "MIT")))
    (is (false? (deprecated-id? "gnuplot")))
    (is (false? (deprecated-id? "OLDAP-2.2.2")))
    (is (false? (deprecated-id? "GPL-3.0-linking-exception")))
    (is (false? (deprecated-id? "LLVM-exception")))
    (is (false? (deprecated-id? "OpenJDK-assembly-exception-1.0")))))

(deftest non-deprecated-ids-tests
  (testing "We have some non-deprecated-ids"
    (is (pos? (count (non-deprecated-ids)))))
  (testing "non-deprecated-ids are a set"
    (is (instance? java.util.Set (non-deprecated-ids)))))
