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
            [spdx.expressions :refer [parse parse-with-info unparse normalise valid? extract-ids]]))

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
    (is (nil? (parse "MIT and Apache-2.0" {:case-sensitive-conjunctions? true})))                     ; AND clause must be capitalised
    (is (nil? (parse "MIT or Apache-2.0" {:case-sensitive-conjunctions? true})))                      ; OR clause must be capitalised
    (is (nil? (parse "GPL-2.0 with Classpath-exception-2.0" {:case-sensitive-conjunctions? true}))))  ; WITH clause must be capitalised
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
                                                                {:license-id "MIT"}
                                                                {:license-id "BSD-2-Clause"}]]))
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
                                                               [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-ref "bar" :document-ref "foo"}])))
  (testing "Expressions that exercise operator precedence"
    (is (= (parse "GPL-2.0-only AND Apache-2.0 OR MIT")       [:or
                                                               [:and {:license-id "GPL-2.0-only"} {:license-id "Apache-2.0"}]
                                                               {:license-id "MIT"}]))
    (is (= (parse "GPL-2.0-only OR Apache-2.0 AND MIT")       [:or
                                                               {:license-id "GPL-2.0-only"}
                                                               [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]]))
    (is (= (parse "GPL-2.0-only AND Apache-2.0 OR MIT AND BSD-3-Clause")
                                                              [:or
                                                               [:and {:license-id "GPL-2.0-only"} {:license-id "Apache-2.0"}]
                                                               [:and {:license-id "MIT"} {:license-id "BSD-3-Clause"}]]))
    (is (= (parse "GPL-2.0-only OR Apache-2.0 OR MIT OR BSD-3-Clause OR Unlicense")
                                                              [:or
                                                               {:license-id "GPL-2.0-only"}
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "MIT"}
                                                               {:license-id "BSD-3-Clause"}
                                                               {:license-id "Unlicense"}]))
    (is (= (parse "GPL-2.0-only AND Apache-2.0 AND MIT AND BSD-3-Clause AND Unlicense")
                                                              [:and
                                                               {:license-id "GPL-2.0-only"}
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "MIT"}
                                                               {:license-id "BSD-3-Clause"}
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
                                                               {:license-id "GPL-2.0-only" :license-exception-id "GCC-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "GPL-2.0-with-GCC-exception+ WITH Classpath-exception-2.0")
                                                              [:and
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "GCC-exception-2.0"}
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}]))))

(deftest unnormalised-parse-tests
  (testing "Simple expressions"
    (is (= (parse "GPL-2.0" {:normalise-gpl-ids? false})      {:license-id "GPL-2.0"})))
  (testing "Compound expressions"
    (is (= (parse "GPL-2.0+" {:normalise-gpl-ids? false})     {:license-id "GPL-2.0" :or-later? true}))
    (is (= (parse "GPL-2.0-only+" {:normalise-gpl-ids? false})
                                                              {:license-id "GPL-2.0-only" :or-later? true}))
    (is (= (parse "Apache-2.0 OR GPL-2.0" {:normalise-gpl-ids? false})
                                                              [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0"}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0+" {:normalise-gpl-ids? false})
                                                              [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0" :or-later? true}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0" {:normalise-gpl-ids? false})
                                                              [:or
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)" {:normalise-gpl-ids? false})
                                                              [:or
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "APACHE-2.0 OR (((((GPL-2.0+ WITH CLASSPATH-EXCEPTION-2.0)))))" {:normalise-gpl-ids? false})
                                                              [:or
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "GPL-2.0"
                                                                :or-later? true
                                                                :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar" {:normalise-gpl-ids? false})
                                                              [:or
                                                               [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]
                                                               {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"}
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
                                                     "(Apache-2.0+ AND MIT) OR GPL-2.0-or-later WITH Classpath-exception-2.0 OR (BSD-2-Clause AND DocumentRef-bar:LicenseRef-foo)"))))

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
    (is (nil? (normalise "MIT and AGPL-3.0" {:case-sensitive-conjunctions? true}))))
  (testing "Simple expressions"
    (is (= (normalise "Apache-2.0")        "Apache-2.0"))
    (is (= (normalise "aPaCHe-2.0")        "Apache-2.0"))
    (is (= (normalise "((bsd-4-clause))")  "BSD-4-Clause"))
    (is (= (normalise "LGPL-3.0")          "LGPL-3.0-only"))
    (is (= (normalise "LGPL-3.0+")         "LGPL-3.0-or-later"))
    (is (= (normalise "LGPL-3.0-or-later") "LGPL-3.0-or-later")))
  (testing "Compound expressions"
    (is (= (normalise "MIT and AGPL-3.0")                                                "MIT AND AGPL-3.0-only"))
    (is (= (normalise "(GPL-2.0 WITH Classpath-exception-2.0)")                          "GPL-2.0-only WITH Classpath-exception-2.0"))
    (is (= (normalise "BSD-2-Clause AND MIT or GPL-2.0+ WITH Classpath-exception-2.0")   "(BSD-2-Clause AND MIT) OR GPL-2.0-or-later WITH Classpath-exception-2.0"))
    (is (= (normalise "(BSD-2-Clause AND MIT) Or GPL-2.0+ WITH Classpath-exception-2.0") "(BSD-2-Clause AND MIT) OR GPL-2.0-or-later WITH Classpath-exception-2.0"))
    (is (= (normalise "GPL-2.0-with-GCC-exception WiTh Classpath-exception-2.0")         "GPL-2.0-only WITH GCC-exception-2.0 AND GPL-2.0-only WITH Classpath-exception-2.0")))
  (testing "Precedence rules"
    (is (= (normalise "Apache-2.0 OR  (MIT or  BSD-3-Clause)") "Apache-2.0 OR MIT OR BSD-3-Clause"))
    (is (= (normalise "Apache-2.0 and (MIT AND BSD-3-Clause)") "Apache-2.0 AND MIT AND BSD-3-Clause"))
    (is (= (normalise "(Apache-2.0 or  MIT) or  BSD-3-Clause") "Apache-2.0 OR MIT OR BSD-3-Clause"))
    (is (= (normalise "(Apache-2.0 and MIT) and BSD-3-Clause") "Apache-2.0 AND MIT AND BSD-3-Clause"))
    (is (= (normalise "Apache-2.0 oR  MIT aNd BSD-3-Clause")   "Apache-2.0 OR (MIT AND BSD-3-Clause)"))
    (is (= (normalise "Apache-2.0 AnD MIT Or  BSD-3-Clause")   "(Apache-2.0 AND MIT) OR BSD-3-Clause"))
    (is (= (normalise "Apache-2.0 or  MIT and BSD-3-Clause or Unlicense")
                                                               "Apache-2.0 OR (MIT AND BSD-3-Clause) OR Unlicense"))
    (is (= (normalise "Apache-2.0 AND MIT OR BSD-3-Clause and Unlicense")
                                                               "(Apache-2.0 AND MIT) OR (BSD-3-Clause AND Unlicense)"))
    (is (= (normalise "Apache-2.0 OR (MIT and BSD-3-Clause OR Unlicense)")
                                                               "Apache-2.0 OR (MIT AND BSD-3-Clause) OR Unlicense"))
    (is (= (normalise "mit or bsd-3-clause AND apache-2.0 and beerware OR epl-2.0 and mpl-2.0 OR unlicense and lgpl-3.0 OR wtfpl or glwtpl OR hippocratic-2.1")
                                                               "MIT OR (BSD-3-Clause AND Apache-2.0 AND Beerware) OR (EPL-2.0 AND MPL-2.0) OR (Unlicense AND LGPL-3.0-only) OR WTFPL OR GLWTPL OR Hippocratic-2.1"))
    (is (= (normalise "MIT or (BSD-3-Clause OR (Apache-2.0 OR (Beerware OR (EPL-2.0 OR (MPL-2.0 OR (Unlicense OR (LGPL-3.0-only OR (WTFPL OR (GLWTPL OR (Hippocratic-2.1))))))))))")
                                                               "MIT OR BSD-3-Clause OR Apache-2.0 OR Beerware OR EPL-2.0 OR MPL-2.0 OR Unlicense OR LGPL-3.0-only OR WTFPL OR GLWTPL OR Hippocratic-2.1"))
    (is (= (normalise "MIT and (BSD-3-Clause AND (Apache-2.0 and (Beerware AND (EPL-2.0 and (MPL-2.0 AND (Unlicense and (LGPL-3.0-only AND (WTFPL and (GLWTPL AND (Hippocratic-2.1))))))))))")
                                                               "MIT AND BSD-3-Clause AND Apache-2.0 AND Beerware AND EPL-2.0 AND MPL-2.0 AND Unlicense AND LGPL-3.0-only AND WTFPL AND GLWTPL AND Hippocratic-2.1"))
    (is (= (normalise "MIT and (BSD-3-Clause or (Apache-2.0 and (Beerware or (EPL-2.0 and (MPL-2.0 or (Unlicense and (LGPL-3.0-only or (WTFPL and (GLWTPL or Hippocratic-2.1)))))))))")
                                                               "MIT AND (BSD-3-Clause OR (Apache-2.0 AND (Beerware OR (EPL-2.0 AND (MPL-2.0 OR (Unlicense AND (LGPL-3.0-only OR (WTFPL AND (GLWTPL OR Hippocratic-2.1)))))))))"))
    (is (= (normalise "MIT OR (BSD-3-Clause AND (Apache-2.0 OR (Beerware AND (EPL-2.0 OR (MPL-2.0 AND (Unlicense OR (LGPL-3.0-only AND (WTFPL OR (GLWTPL AND (Hippocratic-2.1))))))))))")
                                                               "MIT OR (BSD-3-Clause AND (Apache-2.0 OR (Beerware AND (EPL-2.0 OR (MPL-2.0 AND (Unlicense OR (LGPL-3.0-only AND (WTFPL OR (GLWTPL AND Hippocratic-2.1)))))))))"))))

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
    (is (not (valid? "MIT or Apache-2.0" {:case-sensitive-conjunctions? true}))))    ; OR clause must be capitalised
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
    (is (= (extract-ids [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0"}]) #{"Apache-2.0" "GPL-2.0"}))
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
