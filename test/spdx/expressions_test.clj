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
            [spdx.expressions :refer [parse parse-with-info unparse normalise valid? extract-ids init!]]))

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
    (is (= (parse "Apache-2.0")                               {:license-id "Apache-2.0"}))
    (is (= (parse "LicenseRef-foo")                           {:license-ref "foo"}))
    (is (= (parse "LicenseRef-foo-bar-blah")                  {:license-ref "foo-bar-blah"}))
    (is (= (parse "DocumentRef-foo:LicenseRef-bar")           {:license-ref "bar" :document-ref "foo"}))
    (is (= (parse "DocumentRef-foo-bar:LicenseRef-blah")      {:license-ref "blah" :document-ref "foo-bar"})))
  (testing "Simple expressions - mixed case"
    (is (= (parse "apache-2.0")                               {:license-id "Apache-2.0"}))
    (is (= (parse "APACHE-2.0")                               {:license-id "Apache-2.0"}))
    (is (= (parse "aPaCHe-2.0")                               {:license-id "Apache-2.0"})))
  (testing "Compound expressions"
    (is (= (parse "Apache-2.0 OR GPL-2.0")                    [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0-only"}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0+")                   [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0-or-later"}]))
    (is (= (parse "   \t   Apache-2.0\nOR\n\tGPL-2.0   \n  ") [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0-only"}]))
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
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)")
                                                              [{:license-id "Apache-2.0"}
                                                               :or
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "APACHE-2.0 OR (((((GPL-2.0+ WITH CLASSPATH-EXCEPTION-2.0)))))")
                                                              [{:license-id "Apache-2.0"}
                                                               :or
                                                               {:license-id "GPL-2.0-or-later"
                                                                :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar")
                                                              [[{:license-id "Apache-2.0"} :and {:license-id "MIT"}]
                                                               :or
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}
                                                               :or
                                                               {:license-ref "bar" :document-ref "foo"}])))
  (testing "Expressions that exercise GPL identifier normalisation"
    (is (= (parse "AGPL-1.0-or-later")                        {:license-id "AGPL-1.0-or-later"}))
    (is (= (parse "AGPL-1.0+")                                {:license-id "AGPL-1.0-or-later"}))
    (is (= (parse "GPL-2.0+")                                 {:license-id "GPL-2.0-or-later"}))
    (is (= (parse "GPL-2.0-only+")                            {:license-id "GPL-2.0-or-later"}))
    (is (= (parse "GPL-2.0-or-later+")                        {:license-id "GPL-2.0-or-later"}))
    (is (= (parse "GPL-2.0-with-GCC-exception WITH Classpath-exception-2.0")
                                                              [{:license-id "GPL-2.0-only" :license-exception-id "GCC-exception-2.0"}
                                                               :and
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}]))))

(deftest unnormalised-parse-tests
  (testing "Simple expressions"
    (is (= (parse "GPL-2.0" {:normalise-gpl-ids? false})      {:license-id "GPL-2.0"})))
  (testing "Compound expressions"
    (is (= (parse "GPL-2.0+" {:normalise-gpl-ids? false})     {:license-id "GPL-2.0" :or-later? true}))
    (is (= (parse "GPL-2.0-only+" {:normalise-gpl-ids? false})
                                                              {:license-id "GPL-2.0-only" :or-later? true}))
    (is (= (parse "Apache-2.0 OR GPL-2.0" {:normalise-gpl-ids? false})
                                                              [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0"}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0+" {:normalise-gpl-ids? false})
                                                              [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0" :or-later? true}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0" {:normalise-gpl-ids? false})
                                                              [{:license-id "Apache-2.0"}
                                                               :or
                                                               {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)" {:normalise-gpl-ids? false})
                                                              [{:license-id "Apache-2.0"}
                                                               :or
                                                               {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "APACHE-2.0 OR (((((GPL-2.0+ WITH CLASSPATH-EXCEPTION-2.0)))))" {:normalise-gpl-ids? false})
                                                              [{:license-id "Apache-2.0"}
                                                               :or
                                                               {:license-id "GPL-2.0"
                                                                :or-later? true
                                                                :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar" {:normalise-gpl-ids? false})
                                                              [[{:license-id "Apache-2.0"} :and {:license-id "MIT"}]
                                                               :or
                                                               {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"}
                                                               :or
                                                               {:license-ref "bar" :document-ref "foo"}]))
    (is (= (parse "GPL-2.0-with-GCC-exception WITH Classpath-exception-2.0" {:normalise-gpl-ids? false})
                                                              {:license-id "GPL-2.0-with-GCC-exception" :license-exception-id "Classpath-exception-2.0"}))))


(deftest parse-with-info-tests
  (testing "Data is returned when parsing fails"
    (is (not (nil? (parse-with-info "AND"))))))

(deftest unparse-tests
  (testing "Nil"
    (is (nil? (unparse nil))))
  (testing "Invalid parse results"
    (is (nil? (unparse [])))
    (is (nil? (unparse {})))
    (is (nil? (unparse 0)))
    (is (nil? (unparse "foo")))
    (is (nil? (unparse "Apache-2.0"))))
  (testing "Simple parse results"
    (is (= (unparse {:license-id "Apache-2.0"})                                              "Apache-2.0"))
    (is (= (unparse {:license-id "Apache-2.0" :or-later? true})                              "Apache-2.0+"))
    (is (= (unparse {:license-id "GPL-2.0" :or-later? true})                                 "GPL-2.0+"))
    (is (= (unparse {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}) "GPL-2.0 WITH Classpath-exception-2.0"))
    (is (= (unparse {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"})
           "GPL-2.0+ WITH Classpath-exception-2.0")))
  (testing "Compound parse results"
    (is (= (unparse [{:license-id "Apache-2.0"} :or  {:license-id "GPL-2.0-only"}])          "Apache-2.0 OR GPL-2.0-only"))
    (is (= (unparse [{:license-id "Apache-2.0"} :and {:license-id "MIT"}])                   "Apache-2.0 AND MIT"))
    (is (= (unparse [{:license-id "Apache-2.0" :or-later? true} :or  {:license-id "GPL-2.0" :or-later? true}])
           "Apache-2.0+ OR GPL-2.0+"))
    (is (= (unparse [{:license-id "Apache-2.0"}
                     :or
                     {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"}])
           "Apache-2.0 OR GPL-2.0+ WITH Classpath-exception-2.0"))
    (is (= (unparse [[{:license-id "Apache-2.0"} :and {:license-id "MIT"}]
                      :or
                      {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"}
                      :or
                      {:license-ref "bar" :document-ref "foo"}])
           "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar")))
  (testing "Unparse a parse"
    (is (= (unparse (parse "Apache-2.0"))            "Apache-2.0"))
    (is (= (unparse (parse "APACHE-2.0"))            "Apache-2.0"))
    (is (= (unparse (parse "((APACHE-2.0))"))        "Apache-2.0"))
    (is (= (unparse (parse "Apache-2.0 OR GPL-2.0")) "Apache-2.0 OR GPL-2.0-only"))
    (is (= (unparse (parse "Apache-2.0 OR GPL-2.0+ WITH Classpath-exception-2.0"))
                                                     "Apache-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"))
    (is (= (unparse (parse "Apache-2.0 OR (GPL-2.0+ WITH Classpath-exception-2.0)"))
                                                     "Apache-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"))
    (is (= (unparse (parse "(Apache-2.0+ AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR (BSD-2-Clause AND DocumentRef-bar:LicenseRef-foo)"))
                                                     "(Apache-2.0+ AND MIT) OR GPL-2.0-or-later WITH Classpath-exception-2.0 OR (BSD-2-Clause AND DocumentRef-bar:LicenseRef-foo)"))))

; Note: we keep these short, as the parser is far more extensively exercised by parse-tests and unparse-tests
(deftest normalise-tests
  (testing "Nil, blank, etc."
    (is (nil? (normalise nil)))
    (is (nil? (normalise "")))
    (is (nil? (normalise "  ")))
    (is (nil? (normalise "\n\t"))))
  (testing "Invalid expressions"
    (is (nil? (normalise "AND")))
    (is (nil? (normalise "THIS-IS-NOT-A-LICENSE-ID")))
    (is (nil? (normalise "DocumentRef-foo")))
    (is (nil? (normalise "LicenseRef-this:is:invalid")))
    (is (nil? (normalise "((BSD-2-Clause")))
    (is (nil? (normalise "Classpath-exception-2.0")))
    (is (nil? (normalise "MIT and AGPL-3.0"))))
  (testing "Simple expressions"
    (is (= (normalise "Apache-2.0")        "Apache-2.0"))
    (is (= (normalise "aPaCHe-2.0")        "Apache-2.0"))
    (is (= (normalise "((bsd-4-clause))")  "BSD-4-Clause"))
    (is (= (normalise "LGPL-3.0")          "LGPL-3.0-only"))
    (is (= (normalise "LGPL-3.0+")         "LGPL-3.0-or-later"))
    (is (= (normalise "LGPL-3.0-or-later") "LGPL-3.0-or-later")))
  (testing "Compound expressions"
    (is (= (normalise "(GPL-2.0 WITH Classpath-exception-2.0)")                          "GPL-2.0-only WITH Classpath-exception-2.0"))
    (is (= (normalise "(BSD-2-Clause AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0") "(BSD-2-Clause AND MIT) OR GPL-2.0-or-later WITH Classpath-exception-2.0"))
    (is (= (normalise "GPL-2.0-with-GCC-exception WITH Classpath-exception-2.0")         "GPL-2.0-only WITH GCC-exception-2.0 AND GPL-2.0-only WITH Classpath-exception-2.0"))))

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
    (is (valid? "LicenseRef-foo"))
    (is (valid? "DocumentRef-foo:LicenseRef-bar"))
    (is (valid? "GPL-2.0 WITH Classpath-exception-2.0"))
    (is (valid? "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)"))
    (is (valid? "(APACHE-2.0 AND MIT) OR (((GPL-2.0 WITH CLASSPATH-EXCEPTION-2.0)))"))))

(deftest extract-ids-tests
  (testing "Nil"
    (is (nil? (extract-ids nil))))
  (testing "Simple parse results"
    (is (= (extract-ids {:license-id "Apache-2.0"})                               #{"Apache-2.0"}))
    (is (= (extract-ids [{:license-id "Apache-2.0"} :or {:license-id "GPL-2.0"}]) #{"Apache-2.0" "GPL-2.0"}))
    (is (= (extract-ids [[[[{:license-id "Apache-2.0"}]]]])                       #{"Apache-2.0"})))
  (testing "Include or later"
    (is (= (extract-ids {:license-id "GPL-2.0" :or-later? true} {:include-or-later? false}) #{"GPL-2.0"}))
    (is (= (extract-ids {:license-id "GPL-2.0" :or-later? true} {:include-or-later? true})  #{"GPL-2.0+"})))
  (testing "LicenseRefs"
    (is (= (extract-ids {:license-ref "foo"})                     #{"LicenseRef-foo"}))
    (is (= (extract-ids {:document-ref "foo" :license-ref "bar"}) #{"DocumentRef-foo:LicenseRef-bar"})))
  (testing "Parsed expressions"
    (is (= (extract-ids (parse "Apache-2.0"))            #{"Apache-2.0"}))
    (is (= (extract-ids (parse "GPL-2.0+"))              #{"GPL-2.0-or-later"}))
    (is (= (extract-ids (parse "Apache-2.0+"))           #{"Apache-2.0"}))   ; Note: Apache doesn't have "or-later" variant identifiers
    (is (= (extract-ids (parse "Apache-2.0+") {:include-or-later? true})      #{"Apache-2.0+"}))  ; Note: Apache doesn't have "or-later" variant identifiers
    (is (= (extract-ids (parse "Apache-2.0 OR GPL-2.0")) #{"Apache-2.0" "GPL-2.0-only"}))
    (is (= (extract-ids (parse "Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0"))
                                                         #{"Apache-2.0" "GPL-2.0-only" "Classpath-exception-2.0"}))
    (is (= (extract-ids (parse "Apache-2.0 OR GPL-2.0+ WITH Classpath-exception-2.0"))
                                                         #{"Apache-2.0" "GPL-2.0-or-later" "Classpath-exception-2.0"}))
    (is (= (extract-ids (parse "Apache-2.0 OR GPL-2.0+ WITH Classpath-exception-2.0") {:include-or-later? true})
                                                         #{"Apache-2.0" "GPL-2.0-or-later" "Classpath-exception-2.0"}))
    (is (= (extract-ids (parse "(Apache-2.0 AND MIT) OR (BSD-2-Clause AND (GPL-2.0+ WITH Classpath-exception-2.0))"))
                                                         #{"Apache-2.0" "MIT" "BSD-2-Clause" "GPL-2.0-or-later" "Classpath-exception-2.0"}))))
