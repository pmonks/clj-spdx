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
            [spdx.test-utils]      ; Unused here, but we force it to run first
            [spdx.regexes    :refer [build-re ids-re license-ids-re exception-ids-re license-ref-re addition-ref-re]]))

(deftest build-re-tests
  (testing "Basic tests"
    (is (nil? (build-re nil)))
    (is (nil? (build-re nil {:match-license-refs? true})))
    (is (nil? (build-re nil {:match-addition-refs? true})))
    (is (nil? (build-re nil {:match-license-refs? true :match-addition-refs? true})))
    (is (instance? java.util.regex.Pattern (build-re ["Apache-2.0"]))))
  (let [apache-20-re (build-re ["Apache-2.0"])]
    (testing "matches"
      (is (not (nil? (re-matches apache-20-re "Apache-2.0")))))
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
  (let [apache-20-or-license-ref-re (build-re ["Apache-2.0"] {:match-license-refs? true})]
    (testing "matches"
      (is (not (nil? (re-matches apache-20-or-license-ref-re "Apache-2.0"))))
      (is (not (nil? (re-matches apache-20-or-license-ref-re "LicenseRef-foo-bar")))))
    (testing "finds"
      (is (not (nil? (re-find apache-20-or-license-ref-re "Apache-2.0 LicenseRef-foo-bar"))))
      (is (not (nil? (re-find apache-20-or-license-ref-re "LicenseRef-foo-bar Apache-2.0"))))
      (is (not (nil? (re-find apache-20-or-license-ref-re "foo Apache-2.0 bar LicenseRef-foo-bar blah"))))
      (is (not (nil? (re-find apache-20-or-license-ref-re "foo LicenseRef-foo-bar bar Apache-2.0 blah")))))
    (testing "re-seq"
      (is (= 0 (count (re-seq apache-20-or-license-ref-re ""))))
      (is (= 0 (count (re-seq apache-20-or-license-ref-re "foobar"))))
      (is (= 1 (count (re-seq apache-20-or-license-ref-re "Apache-2.0"))))
      (is (= 2 (count (re-seq apache-20-or-license-ref-re "Apache-2.0 LicenseRef-foo-bar "))))
      (is (= 3 (count (re-seq apache-20-or-license-ref-re "foo;Apache-2.0 bar LicenseRef-foo-bar Apache-2.0 blah")))))
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

; We keep these short as most variations are exercised via build-re-test
(deftest ids-re-tests
  (testing "Basic tests"
    (is (not (nil? (ids-re))))
    (is (instance? java.util.regex.Pattern (ids-re)))
    (is (= (ids-re) (ids-re))))  ; Ensure regex is cached
  (testing "matches"
    (is (not (nil? (re-matches (ids-re) "Apache-2.0"))))
    (is (not (nil? (re-matches (ids-re) "MIT"))))
    (is (not (nil? (re-matches (ids-re) "GPL-2.0"))))                  ; Deprecated id
    (is (not (nil? (re-matches (ids-re) "LicenseRef-foo"))))
    (is (not (nil? (re-matches (ids-re) "DocumentRef-foo:LicenseRef-bar"))))
    (is (not (nil? (re-matches (ids-re) "Classpath-exception-2.0"))))
    (is (not (nil? (re-matches (ids-re) "AdditionRef-foo"))))
    (is (not (nil? (re-matches (ids-re) "DocumentRef-foo:AdditionRef-bar")))))
  (testing "non-matches"
    (is (nil? (re-matches (ids-re) "Apache-200")))
    (is (nil? (re-matches (ids-re) " Apache-2.0 ")))
    (is (nil? (re-matches (ids-re) "Apache-2.0 GPL-2.0")))
    (is (nil? (re-matches (ids-re) "foobar"))))
  (testing "finds"
    (is (not (nil? (re-find (ids-re) "Apache-2.0"))))
    (is (not (nil? (re-find (ids-re) " Apache-2.0 "))))
    (is (not (nil? (re-find (ids-re) "Apache-1.1 Apache-2.0 GPL-2.0")))))
  (testing "re-seq"
    (is (= 0 (count (re-seq (ids-re) ""))))
    (is (= 0 (count (re-seq (ids-re) "foobar"))))
    (is (= 1 (count (re-seq (ids-re) "MIT"))))
    (is (= 2 (count (re-seq (ids-re) "foo MIT bar X11 blah"))))
    (is (= 4 (count (re-seq (ids-re) "foo;Apache-2.0 bar DocumentRef-foo:LicenseRef-bar Beerware blah DocumentRef-foo:AdditionRef-bar blahblah"))))))

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
    (is (not (nil? (re-matches (license-ref-re) "DocumentRef-foo:LicenseRef-bar")))))
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
    (is (not (nil? (re-matches (addition-ref-re) "DocumentRef-foo:AdditionRef-bar"))))))
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
