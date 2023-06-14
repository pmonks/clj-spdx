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
            [spdx.expressions :refer [parse parse-with-info valid? extract-ids init!]]))

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
    (is (nil? (parse "AND")))                                         ; Naked conjunction
    (is (nil? (parse "OR")))                                          ; Naked disjunction
    (is (nil? (parse "WITH")))                                        ; Naked WITH clause
    (is (nil? (parse "+")))                                           ; Naked + ("and later" indicator)
    (is (nil? (parse "THIS-IS-NOT-A-LICENSE-ID")))                    ; Non-existent license id
    (is (nil? (parse "DocumentRef")))                                 ; DocumentRef without id
    (is (nil? (parse "DocumentRef-")))                                ; DocumentRef without id
    (is (nil? (parse "DocumentRef-foo")))                             ; DocumentRef without LicenseRef
    (is (nil? (parse "LicenseRef")))                                  ; LicenseRef without id
    (is (nil? (parse "LicenseRef-")))                                 ; LicenseRef without id
    (is (nil? (parse "DocumentRef:LicenseRef")))                      ; DocumentRef and LicenseRef without ids
    (is (nil? (parse "DocumentRef-:LicenseRef-")))                    ; DocumentRef and LicenseRef without ids
    (is (nil? (parse "LicenseRef-this:is:invalid")))                  ; Invalid characters in LicenseRef id
    (is (nil? (parse "LicenseRef-also_invalid")))                     ; Invalid characters in LicenseRef id
    (is (nil? (parse "DocumentRef-also_invalid:LicenseRef-foo")))     ; Invalid characters in DocumentRef id
    (is (nil? (parse "((Apache-2.0")))                                ; Mismatched parens
    (is (nil? (parse "Apache-2.0))")))                                ; Mismatched parens
    (is (nil? (parse "((Apache-2.0)")))                               ; Mismatched parens
    (is (nil? (parse "(Apache-2.0))")))                               ; Mismatched parens
    (is (nil? (parse "Classpath-exception-2.0")))                     ; License exception without "<license> WITH " first
    (is (nil? (parse "MIT and Apache-2.0")))                          ; AND clause must be capitalised
    (is (nil? (parse "MIT or Apache-2.0")))                           ; OR clause must be capitalised
    (is (nil? (parse "GPL-2.0 with Classpath-exception-2.0"))))       ; WITH clause must be capitalised
  (testing "Simple expressions"
    (is (= (parse "Apache-2.0")                               [{:license-id  "Apache-2.0"}]))
    (is (= (parse "GPL-2.0+")                                 [{:license-id "GPL-2.0" :or-later true}]))
    (is (= (parse "LicenseRef-foo")                           [{:license-ref "foo"}]))
    (is (= (parse "LicenseRef-foo-bar-blah")                  [{:license-ref "foo-bar-blah"}]))
    (is (= (parse "DocumentRef-foo:LicenseRef-bar")           [{:license-ref "bar" :document-ref "foo"}]))
    (is (= (parse "DocumentRef-foo-bar:LicenseRef-blah")      [{:license-ref "blah" :document-ref "foo-bar"}])))
  (testing "Simple expressions - mixed case"
    (is (= (parse "apache-2.0")                               [{:license-id "Apache-2.0"}]))
    (is (= (parse "APACHE-2.0")                               [{:license-id "Apache-2.0"}]))
    (is (= (parse "aPaCHe-2.0")                               [{:license-id "Apache-2.0"}])))
  (testing "Simple expressions - whitespace and redundant grouping"
    (is (= (parse "   Apache-2.0   ")                         [{:license-id "Apache-2.0"}]))
    (is (= (parse "(((((((((Apache-2.0)))))))))")             [{:license-id "Apache-2.0"}]))
    (is (= (parse "((((((((( \t Apache-2.0 \n\n\t )))))))))") [{:license-id "Apache-2.0"}])))
  (testing "Compound expressions"
    (is (= (parse "Apache-2.0 OR GPL-2.0")                    [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0"}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0+")                   [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0" :or-later true}]))
    (is (= (parse "   \t   Apache-2.0\nOR\n\tGPL-2.0   \n  ") [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0"}]))
    (is (= (parse "Apache-2.0 AND MIT+")                      (parse "((((Apache-2.0)))) AND (MIT+)")))
    (is (= (parse "((((Apache-2.0)))) OR (MIT AND BSD-2-Clause)")
                                                              [{:license-id "Apache-2.0"}
                                                               :or
                                                               [{:license-id "MIT"}
                                                                :and
                                                                {:license-id "BSD-2-Clause"}]]))
    (is (= (parse "Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0")
                                                              [{:license-id "Apache-2.0"}
                                                               :or
                                                               {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)")
                                                              [{:license-id "Apache-2.0"}
                                                               :or
                                                               {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "APACHE-2.0 OR (((((GPL-2.0+ WITH CLASSPATH-EXCEPTION-2.0)))))")
                                                              [{:license-id "Apache-2.0"}
                                                               :or
                                                               {:license-id "GPL-2.0"
                                                                :or-later true
                                                                :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar")
                                                              [[{:license-id "Apache-2.0"} :and {:license-id "MIT"}]
                                                               :or
                                                               {:license-id "GPL-2.0" :or-later true :license-exception-id "Classpath-exception-2.0"}
                                                               :or
                                                               {:license-ref "bar" :document-ref "foo"}]))))

(deftest parse-with-info-tests
  (testing "Data is returned when parsing fails"
    (is (not (nil? (parse-with-info "AND"))))))

; Note: we keep these short, as the parser is far more extensively exercised by parse-tests
(deftest valid?-tests
  (testing "Nil, empty, etc."
    (is (not (valid? nil)))
    (is (not (valid? ""))))
  (testing "Invalid expressions"
    (is (not (valid? "+")))
    (is (not (valid? "AND")))
    (is (not (valid? "Apache")))
    (is (not (valid? "Classpath-exception-2.0")))
    (is (not (valid? "MIT or Apache-2.0"))))    ; OR clause must be capitalised
  (testing "Valid expressions"
    (is (valid? "Apache-2.0"))
    (is (valid? "apache-2.0"))
    (is (valid? "GPL-2.0+"))
    (is (valid? "GPL-2.0 WITH Classpath-exception-2.0"))
    (is (valid? "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)"))
    (is (valid? "(APACHE-2.0 AND MIT) OR (((GPL-2.0 WITH CLASSPATH-EXCEPTION-2.0)))"))))

(deftest extract-ids-tests
  (testing "Nil"
    (is (nil? (extract-ids nil))))
  (testing "Simple parse results"
    (is (= (extract-ids {:license-id "Apache-2.0"})                               #{"Apache-2.0"}))
    (is (= (extract-ids [{:license-id "Apache-2.0"}])                             #{"Apache-2.0"}))
    (is (= (extract-ids [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0"}]) #{"Apache-2.0" "GPL-2.0"}))
    (is (= (extract-ids [[[[{:license-id "Apache-2.0"}]]]])                       #{"Apache-2.0"})))
  (testing "Include or later"
    (is (= (extract-ids {:license-id "GPL-2.0" :or-later true} false) #{"GPL-2.0"}))
    (is (= (extract-ids {:license-id "GPL-2.0" :or-later true} true)  #{"GPL-2.0+"})))
  (testing "Parsed expressions"
    (is (= (extract-ids (parse "Apache-2.0"))            #{"Apache-2.0"}))
    (is (= (extract-ids (parse "Apache-2.0 OR GPL-2.0")) #{"Apache-2.0" "GPL-2.0"}))
    (is (= (extract-ids (parse "Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0"))
                                                         #{"Apache-2.0" "GPL-2.0" "Classpath-exception-2.0"}))
    (is (= (extract-ids (parse "Apache-2.0 OR GPL-2.0+ WITH Classpath-exception-2.0"))
                                                         #{"Apache-2.0" "GPL-2.0" "Classpath-exception-2.0"}))
    (is (= (extract-ids (parse "Apache-2.0 OR GPL-2.0+ WITH Classpath-exception-2.0") true)
                                                         #{"Apache-2.0" "GPL-2.0+" "Classpath-exception-2.0"}))
    (is (= (extract-ids (parse "(Apache-2.0 AND MIT) OR (BSD-2-Clause AND (GPL-2.0+ WITH Classpath-exception-2.0))"))
                                                         #{"Apache-2.0" "MIT" "BSD-2-Clause" "GPL-2.0" "Classpath-exception-2.0"}))))
