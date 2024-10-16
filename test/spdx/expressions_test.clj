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
            [spdx.test-utils]      ; Unused, but we force it to run first
            [spdx.expressions :refer [parse parse-with-info unparse normalise valid? simple? compound? extract-ids walk]]))

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
    (is (nil? (parse "Apache-2.0 AND")))                              ; Dangling operator
    (is (nil? (parse "(Apache-2.0 AND) MIT")))                        ; Bad nesting (parens)
    (is (nil? (parse "(GPL-2.0) WITH Classpath-Exception-2.0")))      ; Bad nesting (parens)
    (is (nil? (parse "(GPL-2.0 WITH) Classpath-Exception-2.0")))      ; Bad nesting (parens)
    (is (nil? (parse "GPL-2.0 (WITH) Classpath-Exception-2.0")))      ; Bad nesting (parens)
    (is (nil? (parse "GPL-2.0 (WITH Classpath-Exception-2.0)")))      ; Bad nesting (parens)
    (is (nil? (parse "GPL-2.0 WITH (Classpath-Exception-2.0)")))      ; Bad nesting (parens)
    (is (nil? (parse "Classpath-exception-2.0")))                     ; License exception without "<license> WITH " first
    (is (nil? (parse "AdditionRef-foo")))                             ; AdditionRef without  "<license> WITH " first
    (is (nil? (parse "DocumentRef-foo:AdditionRef-bar")))             ; AdditionRef without  "<license> WITH " first
    (is (nil? (parse "MIT and Apache-2.0" {:case-sensitive-operators? true})))                     ; AND clause must be capitalised
    (is (nil? (parse "MIT or Apache-2.0" {:case-sensitive-operators? true})))                      ; OR clause must be capitalised
    (is (nil? (parse "GPL-2.0 with Classpath-exception-2.0" {:case-sensitive-operators? true}))))  ; WITH clause must be capitalised
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
    (is (= (parse "Apache-2.0 OR GPL-2.0")                    [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0+")                   [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0-or-later"}]))
    (is (= (parse "   \t   Apache-2.0\nOR\n\tGPL-2.0   \n  ") [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"}]))
    (is (= (parse "Apache-2.0 AND MIT+")                      (parse "((((Apache-2.0)))) AND (MIT+)")))
    (is (= (parse "((((Apache-2.0)))) OR (MIT AND BSD-2-Clause)")
                                                              [:or
                                                               {:license-id "Apache-2.0"}
                                                               [:and
                                                                {:license-id "BSD-2-Clause"}
                                                                {:license-id "MIT"}]]))
    (is (= (parse "Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0")
                                                              [:or
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)")
                                                              [:or
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "APACHE-2.0 OR (((((GPL-2.0+ WITH CLASSPATH-EXCEPTION-2.0)))))")
                                                              [:or
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "GPL-2.0-or-later"
                                                                :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar")
                                                              [:or
                                                               {:license-ref "bar" :document-ref "foo"}
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}
                                                               [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]]))
    (is (= (parse "LicenseRef-foo WITH AdditionRef-bar")      {:license-ref "foo" :addition-ref "bar"}))
    (is (= (parse "DocumentRef-foo:LicenseRef-bar WITH DocumentRef-blah:AdditionRef-banana")
                                                              {:document-ref "foo" :license-ref "bar" :addition-document-ref "blah" :addition-ref "banana"})))
  (testing "Expressions that exercise operator precedence"
    (is (= (parse "GPL-2.0-only AND Apache-2.0 OR MIT")       [:or
                                                               {:license-id "MIT"}
                                                               [:and {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"}]]))
    (is (= (parse "GPL-2.0-only OR Apache-2.0 AND MIT")       [:or
                                                               {:license-id "GPL-2.0-only"}
                                                               [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]]))
    (is (= (parse "GPL-2.0-only AND Apache-2.0 OR MIT AND BSD-3-Clause")
                                                              [:or
                                                               [:and {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"}]
                                                               [:and {:license-id "BSD-3-Clause"} {:license-id "MIT"}]]))
    (is (= (parse "GPL-2.0-only OR Apache-2.0 OR MIT OR BSD-3-Clause OR Unlicense")
                                                              [:or
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "BSD-3-Clause"}
                                                               {:license-id "GPL-2.0-only"}
                                                               {:license-id "MIT"}
                                                               {:license-id "Unlicense"}]))
    (is (= (parse "GPL-2.0-only AND Apache-2.0 AND MIT AND BSD-3-Clause AND Unlicense")
                                                              [:and
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "BSD-3-Clause"}
                                                               {:license-id "GPL-2.0-only"}
                                                               {:license-id "MIT"}
                                                               {:license-id "Unlicense"}])))
  (testing "Expressions that exercise GPL identifier normalisation"
    (is (= (parse "AGPL-1.0-or-later")                        {:license-id "AGPL-1.0-or-later"}))
    (is (= (parse "AGPL-1.0+")                                {:license-id "AGPL-1.0-or-later"}))
    (is (= (parse "GPL-2.0+")                                 {:license-id "GPL-2.0-or-later"}))
    (is (= (parse "GPL-2.0-only+")                            {:license-id "GPL-2.0-or-later"}))
    (is (= (parse "GPL-2.0-or-later+")                        {:license-id "GPL-2.0-or-later"}))
    ; These next couple are pretty cursed...
    (is (= (parse "GPL-2.0-with-classpath-exception WITH Classpath-exception-2.0")
                                                              {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}))
    (is (= (parse "GPL-2.0-with-GCC-exception WITH Classpath-exception-2.0")
                                                              [:and
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "GCC-exception-2.0"}]))
    (is (= (parse "GPL-2.0-with-GCC-exception+ WITH Classpath-exception-2.0")
                                                              [:and
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "GCC-exception-2.0"}])))
  (testing "Expressions that exercise collapsing redundant clauses"
    (is (= (parse "Apache-2.0 OR Apache-2.0")                 {:license-id "Apache-2.0"}))
    (is (= (parse "Apache-2.0 AND Apache-2.0" {:collapse-redundant-clauses? true})
                                                              {:license-id "Apache-2.0"}))
    (is (= (parse "Apache-2.0 AND Apache-2.0" {:collapse-redundant-clauses? false})
                                                              [:and
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "Apache-2.0"}]))
    (is (= (parse "Apache-2.0 OR Apache-2.0 AND MIT")         [:or {:license-id "Apache-2.0"} [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]]))  ; Note: an example of one that should NOT be collapsed, since that would change the meaning of the expression
    (is (= (parse "Apache-2.0 AND Apache-2.0 OR MIT")         [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "Apache-2.0 OR MIT OR Apache-2.0")          [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "Apache-2.0 AND MIT AND Apache-2.0")        [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "Apache-2.0 AND Apache-2.0 OR Apache-2.0 AND Apache-2.0")
                                                              {:license-id "Apache-2.0"}))
    (is (= (parse "Apache-2.0 AND (Apache-2.0 OR (Apache-2.0 AND Apache-2.0))")
                                                              {:license-id "Apache-2.0"}))
    (is (= (parse "(Apache-2.0 AND Apache-2.0) OR (Apache-2.0 AND Apache-2.0)")
                                                              {:license-id "Apache-2.0"}))
    (is (= (parse "GPL-2.0-or-later WITH Classpath-exception-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0")
                                                              {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}))
    (is (= (parse "GPL-2.0+ WITH Classpath-exception-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0")
                                                              {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}))
    (is (= (parse "LicenseRef-foo OR LicenseRef-foo")         {:license-ref "foo"}))
    (is (= (parse "DocumentRef-foo:LicenseRef-bar AND DocumentRef-foo:LicenseRef-bar ")
                                                              {:document-ref "foo" :license-ref "bar"})))
  (testing "Expressions that exercise sorting of licenses"
    (is (= (parse "Apache-2.0 OR MIT")                        [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "MIT OR Apache-2.0")                        [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "MIT OR Apache-2.0" {:sort-licenses? true}) [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "MIT OR Apache-2.0" {:sort-licenses? false}) [:or {:license-id "MIT"} {:license-id "Apache-2.0"}]))))

(deftest unnormalised-parse-tests
  (testing "Simple expressions - normalisation"
    (is (= (parse "AGPL-1.0"                               {:normalise-deprecated-ids? true}) {:license-id "AGPL-1.0-only"}))
    (is (= (parse "GPL-2.0"                                {:normalise-deprecated-ids? true}) {:license-id "GPL-2.0-only"}))
    (is (= (parse "StandardML-NJ"                          {:normalise-deprecated-ids? true}) {:license-id "SMLNJ"}))
    (is (= (parse "Apache-2.0 WITH Nokia-Qt-exception-1.1" {:normalise-deprecated-ids? true}) {:license-id "Apache-2.0" :license-exception-id "Qt-LGPL-exception-1.1"})))
  (testing "Simple expressions - no normalisation"
    (is (= (parse "AGPL-1.0"                               {:normalise-deprecated-ids? false}) {:license-id "AGPL-1.0"}))
    (is (= (parse "GPL-2.0"                                {:normalise-deprecated-ids? false}) {:license-id "GPL-2.0"}))
    (is (= (parse "StandardML-NJ"                          {:normalise-deprecated-ids? false}) {:license-id "StandardML-NJ"}))
    (is (= (parse "Apache-2.0 WITH Nokia-Qt-exception-1.1" {:normalise-deprecated-ids? false}) {:license-id "Apache-2.0" :license-exception-id "Nokia-Qt-exception-1.1"})))
  (testing "Compound expressions"
    (is (= (parse "GPL-2.0+" {:normalise-deprecated-ids? false})
           {:license-id "GPL-2.0" :or-later? true}))
    (is (= (parse "GPL-2.0-only+" {:normalise-deprecated-ids? false})
           {:license-id "GPL-2.0-only" :or-later? true}))
    (is (= (parse "Apache-2.0 OR GPL-2.0" {:normalise-deprecated-ids? false})
           [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0"}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0+" {:normalise-deprecated-ids? false})
           [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0" :or-later? true}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0" {:normalise-deprecated-ids? false})
           [:or
            {:license-id "Apache-2.0"}
            {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)" {:normalise-deprecated-ids? false})
           [:or
            {:license-id "Apache-2.0"}
            {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "APACHE-2.0 OR (((((GPL-2.0+ WITH CLASSPATH-EXCEPTION-2.0)))))" {:normalise-deprecated-ids? false})
           [:or
            {:license-id "Apache-2.0"}
            {:license-id "GPL-2.0"
             :or-later? true
             :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar" {:normalise-deprecated-ids? false})
           [:or
            {:license-ref "bar" :document-ref "foo"}
            {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"}
            [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]]))
    (is (= (parse "GPL-2.0-with-GCC-exception WITH Classpath-exception-2.0" {:normalise-deprecated-ids? false})
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
    (is (= (unparse {:license-ref "foo"})                                                    "LicenseRef-foo"))
    (is (= (unparse {:document-ref "foo" :license-ref "bar"})                                "DocumentRef-foo:LicenseRef-bar"))
    (is (= (unparse {:license-id "Apache-2.0" :or-later? true})                              "Apache-2.0+"))
    (is (= (unparse {:license-id "GPL-2.0" :or-later? true})                                 "GPL-2.0+"))
    (is (= (unparse {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}) "GPL-2.0 WITH Classpath-exception-2.0"))
    (is (= (unparse {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"})
           "GPL-2.0+ WITH Classpath-exception-2.0"))
    (is (= (unparse {:license-ref "foo" :addition-ref "bar"})                                "LicenseRef-foo WITH AdditionRef-bar"))
    (is (= (unparse {:document-ref "foo" :license-ref "bar" :addition-document-ref "blah" :addition-ref "banana"})
           "DocumentRef-foo:LicenseRef-bar WITH DocumentRef-blah:AdditionRef-banana")))
  (testing "Compound parse results"
    (is (= (unparse [:or  {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"}])          "Apache-2.0 OR GPL-2.0-only"))
    (is (= (unparse [:and {:license-id "Apache-2.0"} {:license-id "MIT"}])                   "Apache-2.0 AND MIT"))
    (is (= (unparse [:or  {:license-id "Apache-2.0" :or-later? true} {:license-id "GPL-2.0" :or-later? true}])
           "Apache-2.0+ OR GPL-2.0+"))
    (is (= (unparse [:or
                     {:license-id "Apache-2.0"}
                     {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"}])
           "Apache-2.0 OR GPL-2.0+ WITH Classpath-exception-2.0"))
    (is (= (unparse [:or
                      [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]
                      {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"}
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
                                                     "GPL-2.0-or-later WITH Classpath-exception-2.0 OR (Apache-2.0+ AND MIT) OR (BSD-2-Clause AND DocumentRef-bar:LicenseRef-foo)"))))

; Note: we keep these short(ish), as the parser is far more extensively exercised by parse-tests and unparse-tests
; Precedence rule tests are only here however, as they're less cumbersome to test using normalise
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
    (is (nil? (normalise "MIT and AGPL-3.0" {:case-sensitive-operators? true}))))
  (testing "Simple expressions"
    (is (= (normalise "Apache-2.0")                     "Apache-2.0"))
    (is (= (normalise "aPaCHe-2.0")                     "Apache-2.0"))
    (is (= (normalise "((bsd-4-clause))")               "BSD-4-Clause"))
    (is (= (normalise "LGPL-3.0")                       "LGPL-3.0-only"))
    (is (= (normalise "LGPL-3.0+")                      "LGPL-3.0-or-later"))
    (is (= (normalise "LGPL-3.0-or-later")              "LGPL-3.0-or-later"))
    (is (= (normalise "LicenseRef-foo")                 "LicenseRef-foo"))
    (is (= (normalise "DocumentRef-foo:LicenseRef-bar") "DocumentRef-foo:LicenseRef-bar")))
  (testing "Compound expressions"
    (is (= (normalise "MIT and AGPL-3.0")                                                        "AGPL-3.0-only AND MIT"))
    (is (= (normalise "(GPL-2.0 WITH Classpath-exception-2.0)")                                  "GPL-2.0-only WITH Classpath-exception-2.0"))
    (is (= (normalise "BSD-2-Clause AND MIT or GPL-2.0+ WITH Classpath-exception-2.0")           "GPL-2.0-or-later WITH Classpath-exception-2.0 OR (BSD-2-Clause AND MIT)"))
    (is (= (normalise "(BSD-2-Clause AND MIT) Or GPL-2.0+ WITH Classpath-exception-2.0")         "GPL-2.0-or-later WITH Classpath-exception-2.0 OR (BSD-2-Clause AND MIT)"))
    (is (= (normalise "GPL-2.0-with-GCC-exception WiTh Classpath-exception-2.0")                 "GPL-2.0-only WITH Classpath-exception-2.0 AND GPL-2.0-only WITH GCC-exception-2.0"))
    (is (= (normalise "LicenseRef-foo WITH Classpath-exception-2.0")                             "LicenseRef-foo WITH Classpath-exception-2.0"))
    (is (= (normalise "Apache-2.0 WITH AdditionRef-foo")                                         "Apache-2.0 WITH AdditionRef-foo"))
    (is (= (normalise "LicenseRef-foo with AdditionRef-blah")                                    "LicenseRef-foo WITH AdditionRef-blah"))
    (is (= (normalise "DocumentRef-foo:LicenseRef-bar wItH DocumentRef-blah:AdditionRef-banana") "DocumentRef-foo:LicenseRef-bar WITH DocumentRef-blah:AdditionRef-banana")))
  (testing "Precedence rules"
    (is (= (normalise "Apache-2.0 OR  (MIT or  BSD-3-Clause)") "Apache-2.0 OR BSD-3-Clause OR MIT"))
    (is (= (normalise "Apache-2.0 and (MIT AND BSD-3-Clause)") "Apache-2.0 AND BSD-3-Clause AND MIT"))
    (is (= (normalise "((((((Apache-2.0)))))) AND (MIT and BSD-3-Clause)")
                                                               "Apache-2.0 AND BSD-3-Clause AND MIT"))
    (is (= (normalise "(Apache-2.0 or  MIT) or  BSD-3-Clause") "Apache-2.0 OR BSD-3-Clause OR MIT"))
    (is (= (normalise "(Apache-2.0 and MIT) and BSD-3-Clause") "Apache-2.0 AND BSD-3-Clause AND MIT"))
    (is (= (normalise "Apache-2.0 oR  MIT aNd BSD-3-Clause")   "Apache-2.0 OR (BSD-3-Clause AND MIT)"))
    (is (= (normalise "Apache-2.0 AnD MIT Or  BSD-3-Clause")   "BSD-3-Clause OR (Apache-2.0 AND MIT)"))
    (is (= (normalise "Apache-2.0 or  MIT and BSD-3-Clause or Unlicense")
                                                               "Apache-2.0 OR Unlicense OR (BSD-3-Clause AND MIT)"))
    (is (= (normalise "Apache-2.0 AND MIT OR BSD-3-Clause and Unlicense")
                                                               "(Apache-2.0 AND MIT) OR (BSD-3-Clause AND Unlicense)"))
    (is (= (normalise "Apache-2.0 OR (MIT and BSD-3-Clause OR Unlicense)")
                                                               "Apache-2.0 OR Unlicense OR (BSD-3-Clause AND MIT)"))
    (is (= (normalise "mit or bsd-3-clause AND apache-2.0 and beerware OR epl-2.0 and mpl-2.0 OR unlicense and lgpl-3.0 OR wtfpl or glwtpl OR hippocratic-2.1")
                                                               "GLWTPL OR Hippocratic-2.1 OR MIT OR WTFPL OR (EPL-2.0 AND MPL-2.0) OR (LGPL-3.0-only AND Unlicense) OR (Apache-2.0 AND BSD-3-Clause AND Beerware)"))
    (is (= (normalise "MIT or (BSD-3-Clause OR (Apache-2.0 OR (Beerware OR (EPL-2.0 OR (MPL-2.0 OR (Unlicense OR (LGPL-3.0-only OR (WTFPL OR (GLWTPL OR (Hippocratic-2.1))))))))))")
                                                               "Apache-2.0 OR BSD-3-Clause OR Beerware OR EPL-2.0 OR GLWTPL OR Hippocratic-2.1 OR LGPL-3.0-only OR MIT OR MPL-2.0 OR Unlicense OR WTFPL"))
    (is (= (normalise "MIT and (BSD-3-Clause AND (Apache-2.0 and (Beerware AND (EPL-2.0 and (MPL-2.0 AND (Unlicense and (LGPL-3.0-only AND (WTFPL and (GLWTPL AND (Hippocratic-2.1))))))))))")
                                                               "Apache-2.0 AND BSD-3-Clause AND Beerware AND EPL-2.0 AND GLWTPL AND Hippocratic-2.1 AND LGPL-3.0-only AND MIT AND MPL-2.0 AND Unlicense AND WTFPL"))
    (is (= (normalise "MIT and (BSD-3-Clause or (Apache-2.0 and (Beerware or (EPL-2.0 and (MPL-2.0 or (Unlicense and (LGPL-3.0-only or (WTFPL and (GLWTPL or Hippocratic-2.1)))))))))")
                                                               "MIT AND (BSD-3-Clause OR (Apache-2.0 AND (Beerware OR (EPL-2.0 AND (MPL-2.0 OR (Unlicense AND (LGPL-3.0-only OR (WTFPL AND (GLWTPL OR Hippocratic-2.1)))))))))"))
    (is (= (normalise "MIT OR (BSD-3-Clause AND (Apache-2.0 OR (Beerware AND (EPL-2.0 OR (MPL-2.0 AND (Unlicense OR (LGPL-3.0-only AND (WTFPL OR (GLWTPL AND (Hippocratic-2.1))))))))))")
                                                               "MIT OR (BSD-3-Clause AND (Apache-2.0 OR (Beerware AND (EPL-2.0 OR (MPL-2.0 AND (Unlicense OR (LGPL-3.0-only AND (WTFPL OR (GLWTPL AND Hippocratic-2.1)))))))))")))
  (testing "Collapsing redundant expressions"
    (is (= (normalise "Apache-2.0 OR Apache-2.0")              "Apache-2.0"))
    (is (= (normalise "Apache-2.0 OR (Apache-2.0 AND (Apache-2.0 AND Apache-2.0) OR Apache-2.0)")
                                                               "Apache-2.0")))
  (testing "Sorting of licenses within the parse tree"
    (is (= (normalise "Apache-2.0 OR MIT")                     (normalise "MIT OR Apache-2.0")))))

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
    (is (not (valid? "MIT or Apache-2.0" {:case-sensitive-operators? true}))))    ; OR clause must be capitalised
  (testing "Valid expressions"
    (is (valid? "Apache-2.0"))
    (is (valid? "apache-2.0"))
    (is (valid? "GPL-2.0+"))
    (is (valid? "LicenseRef-foo"))
    (is (valid? "DocumentRef-foo:LicenseRef-bar"))
    (is (valid? "GPL-2.0 WITH Classpath-exception-2.0"))
    (is (valid? "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)"))
    (is (valid? "(APACHE-2.0 AND MIT) OR (((GPL-2.0 WITH CLASSPATH-EXCEPTION-2.0)))"))))

(deftest simple?-tests
  (testing "Nil, empty, etc."
    (is (nil? (simple? nil)))
    (is (nil? (simple? ""))))
  (testing "Invalid expressions"
    (is (nil? (simple? "+")))
    (is (nil? (simple? "AND")))
    (is (nil? (simple? "Apache")))
    (is (nil? (simple? "Classpath-exception-2.0")))
    (is (nil? (simple? "MIT or Apache-2.0" {:case-sensitive-operators? true}))))    ; OR clause must be capitalised
  (testing "Valid expressions - simple"
    (is (true? (simple? "Apache-2.0")))
    (is (true? (simple? "GPL-2.0-or-later WITH Classpath-exception-2.0"))))
  (testing "Valid expressions - compound"
    (is (false? (simple? "Apache-2.0 AND MIT")))
    (is (false? (simple? "GPL-2.0-or-later WITH Classpath-exception-2.0 OR EPL-1.0")))))

(deftest compound?-tests
  (testing "Nil, empty, etc."
    (is (nil? (compound? nil)))
    (is (nil? (compound? ""))))
  (testing "Invalid expressions"
    (is (nil? (compound? "+")))
    (is (nil? (compound? "AND")))
    (is (nil? (compound? "Apache")))
    (is (nil? (compound? "Classpath-exception-2.0")))
    (is (nil? (compound? "MIT or Apache-2.0" {:case-sensitive-operators? true}))))    ; OR clause must be capitalised
  (testing "Valid expressions - simple"
    (is (false? (compound? "Apache-2.0")))
    (is (false? (compound? "GPL-2.0-or-later WITH Classpath-exception-2.0"))))
  (testing "Valid expressions - compound"
    (is (true? (compound? "Apache-2.0 AND MIT")))
    (is (true? (compound? "GPL-2.0-or-later WITH Classpath-exception-2.0 OR EPL-1.0")))))

(deftest extract-ids-tests
  (testing "Nil"
    (is (nil? (extract-ids nil))))
  (testing "Simple parse results"
    (is (= (extract-ids {:license-id "Apache-2.0"})                               #{"Apache-2.0"}))
    (is (= (extract-ids [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0"}]) #{"Apache-2.0" "GPL-2.0"}))
    (is (= (extract-ids [[[[{:license-id "Apache-2.0"}]]]])                       #{"Apache-2.0"})))
  (testing "Include or later"
    (is (= (extract-ids {:license-id "GPL-2.0" :or-later? true} {:include-or-later? false}) #{"GPL-2.0"}))
    (is (= (extract-ids {:license-id "GPL-2.0" :or-later? true} {:include-or-later? true})  #{"GPL-2.0+"})))
  (testing "LicenseRefs and AdditionRefs"
    (is (= (extract-ids {:license-ref "foo"})                                                                          #{"LicenseRef-foo"}))
    (is (= (extract-ids {:document-ref "foo" :license-ref "bar"})                                                      #{"DocumentRef-foo:LicenseRef-bar"}))
    (is (= (extract-ids {:license-ref "foo" :addition-ref "bar"})                                                      #{"LicenseRef-foo" "AdditionRef-bar"}))
    (is (= (extract-ids {:document-ref "foo" :license-ref "bar" :addition-document-ref "blah" :addition-ref "banana"}) #{"DocumentRef-foo:LicenseRef-bar" "DocumentRef-blah:AdditionRef-banana"})))
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

; We keep these fairly short since other functions are implemented using walk (including unparse), so any bugs in it are highly likely to show up elsewhere
(deftest walk-tests
  (testing "Nil, empty, etc."
    (is (nil? (walk nil nil)))
    (is (nil? (walk nil (parse nil))))
    (is (nil? (walk nil (parse ""))))
    (is (nil? (walk nil (parse "INVALID SPDX EXPRESSION!!!!")))))
  (testing "No walk functions (i.e. identity semantics)"
    (is (= (walk nil (parse "Apache-2.0"))        (parse "Apache-2.0")))
    (is (= (walk nil (parse "MIT OR Apache-2.0")) (parse "MIT OR Apache-2.0")))
    (is (= (walk nil (parse "GPL-2.0-with-GCC-exception WiTh Classpath-exception-2.0 AND (Apache-2.0 OR MIT)"))
           (parse "GPL-2.0-with-GCC-exception WiTh Classpath-exception-2.0 AND (Apache-2.0 OR MIT)"))))
  (testing "Walk functions"
    (is (= (walk {:op-fn      name}        (parse "MIT OR Apache-2.0")) ["or" {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (walk {:license-fn :license-id} (parse "MIT OR Apache-2.0")) [:or "Apache-2.0" "MIT"]))
    (is (= (walk {:group-fn   #(count %2)} (parse "MIT OR Apache-2.0")) 3))))
