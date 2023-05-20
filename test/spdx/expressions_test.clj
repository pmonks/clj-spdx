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

(ns spdx.expressions-test
  (:require [clojure.test     :refer [deftest testing is]]
;            [spdx.test-utils  :refer [equivalent-colls?]]
            [spdx.expressions :refer [parse parse-with-info valid? init!]]))

(deftest init!-tests
  (testing "Nil response"
    (is (nil? (init!)))))

(deftest parse-tests
  (testing "Nil, empty, etc."
    (is (nil? (parse nil)))
    (is (nil? (parse "")))
    (is (nil? (parse "       ")))
    (is (nil? (parse "\t\n"))))
  (testing "Error cases"
    (is (nil? (parse "AND")))                         ; Naked conjunction
    (is (nil? (parse "OR")))                          ; Naked conjunction
    (is (nil? (parse "WITH")))                        ; Naked WITH clause
    (is (nil? (parse "+")))                           ; Naked + ("and later" indicator)
    (is (nil? (parse "THIS-IS-NOT-A-LICENSE-ID")))    ; Non-existent license id
    (is (nil? (parse "DocumentRef")))                 ; DocumentRef without id
    (is (nil? (parse "DocumentRef-")))                ; DocumentRef without id
    (is (nil? (parse "DocumentRef-foo")))             ; DocumentRef without LicenseRef
    (is (nil? (parse "LicenseRef")))                  ; LicenseRef without id
    (is (nil? (parse "LicenseRef-")))                 ; LicenseRef without id
    (is (nil? (parse "DocumentRef:LicenseRef")))      ; DocumentRef and LicenseRef without ids
    (is (nil? (parse "DocumentRef-:LicenseRef-")))    ; DocumentRef and LicenseRef without ids
    (is (nil? (parse "((Apache-2.0")))                ; Mismatched parens
    (is (nil? (parse "Apache-2.0))")))                ; Mismatched parens
    (is (nil? (parse "((Apache-2.0)")))               ; Mismatched parens
    (is (nil? (parse "(Apache-2.0))")))               ; Mismatched parens
    (is (nil? (parse "Classpath-exception-2.0"))))    ; License exception without "<license> WITH " first
  (testing "Simple expressions"
    (is (= (parse "Apache-2.0")                               [:license-expression [:license-id  "Apache-2.0"]]))
    (is (= (parse "LicenseRef-foo")                           [:license-expression [:license-ref "LicenseRef-foo"]]))
    (is (= (parse "LicenseRef-foo-bar-blah")                  [:license-expression [:license-ref "LicenseRef-foo-bar-blah"]]))
    (is (= (parse "DocumentRef-foo:LicenseRef-bar")           [:license-expression [:license-ref "DocumentRef-foo:LicenseRef-bar"]])))
  (testing "Simple expressions - mixed case"
    (is (= (parse "apache-2.0")                               [:license-expression [:license-id "Apache-2.0"]]))
    (is (= (parse "APACHE-2.0")                               [:license-expression [:license-id "Apache-2.0"]]))
    (is (= (parse "aPaCHe-2.0")                               [:license-expression [:license-id "Apache-2.0"]])))
  (testing "Simple expressions - whitespace and redundant grouping"
    (is (= (parse "   Apache-2.0   ")                         [:license-expression [:license-id "Apache-2.0"]]))
    (is (= (parse "(((((((((Apache-2.0)))))))))")             [:license-expression [:license-id "Apache-2.0"]]))
    (is (= (parse "((((((((( \t Apache-2.0 \n\n\t )))))))))") [:license-expression [:license-id "Apache-2.0"]])))
  (testing "Compound expressions"
    (is (= (parse "GPL-2.0+")                                 [:license-expression [[:license-id "GPL-2.0"] :or-later]]))
    (is (= (parse "Apache-2.0 OR GPL-2.0")                    [:license-expression [[:license-id "Apache-2.0"] :or [:license-id "GPL-2.0"]]]))
    (is (= (parse "   \t   Apache-2.0\nOR\n\tGPL-2.0   \n  ") [:license-expression [[:license-id "Apache-2.0"] :or [:license-id "GPL-2.0"]]]))
    (is (= (parse "Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0")
                                                              [:license-expression
                                                               [[:license-id "Apache-2.0"]
                                                                :or
                                                                [:license-id "GPL-2.0"]
                                                                :with
                                                                [:license-exception-id "Classpath-exception-2.0"]]]))
    (is (= (parse "Apache-2.0 OR (GPL-2.0 WITH Classpath-exception-2.0)")   ; Normalisation of redundantly nested WITH clause is not yet implemented
                                                              [:license-expression
                                                               [[:license-id "Apache-2.0"]
                                                                :or
                                                                [:license-expression
                                                                 [[:license-id "GPL-2.0"]
                                                                 :with
                                                                 [:license-exception-id "Classpath-exception-2.0"]]]]]))
    (is (= (parse "Apache-2.0 OR (GPL-2.0+ WITH Classpath-exception-2.0)")   ; Normalisation of redundantly nested WITH clause is not yet implemented
                                                              [:license-expression
                                                               [[:license-id "Apache-2.0"]
                                                               :or
                                                               [:license-expression
                                                                [[:license-id "GPL-2.0"] :or-later
                                                                 :with
                                                                 [:license-exception-id "Classpath-exception-2.0"]]]]]))
    (is (= (parse "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar")
                                                              [:license-expression
                                                               [[:license-expression
                                                                [[:license-id "Apache-2.0"]
                                                                 :and
                                                                 [:license-id "MIT"]]]
                                                                :or
                                                                [:license-id "GPL-2.0"] :or-later :with [:license-exception-id "Classpath-exception-2.0"]
                                                                :or
                                                                [:license-ref "DocumentRef-foo:LicenseRef-bar"]]]))))

(deftest valid?-tests
  (testing "Nil, empty, etc."
    (is (not (valid? nil)))
    (is (not (valid? "")))))
