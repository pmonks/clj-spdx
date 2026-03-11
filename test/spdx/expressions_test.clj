;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.expressions-test
  (:require [clojure.test     :refer [deftest testing is]]
            [spdx.test-utils]      ; Unused, but we force it to run first
            [spdx.expressions :refer [parse parse-with-info unparse canonicalise valid? simple? compound? extract-ids walk]]))

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
    (is (nil? (parse "LicenseRef-foo+")))                             ; Cannot use + with LicenseRefs
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
    (is (nil? (parse "GPL-2.0 WITH Classpath-Exception-2.0+")))       ; Cannot use + with license exceptions
    (is (nil? (parse "GPL-2.0 WITH AdditionRef-foo+")))               ; Cannot use + with AdditionRefs
    (is (nil? (parse "Classpath-exception-2.0")))                     ; Naked license exception
    (is (nil? (parse "AdditionRef-foo")))                             ; naked AdditionRef
    (is (nil? (parse "DocumentRef-foo:AdditionRef-bar")))             ; Naked AdditionRef
    (is (nil? (parse "Apache-2.0 WITH NONE")))                        ; NONE cannot be used in exception position
    (is (nil? (parse "MIT with NOASSERTION"))))                       ; NOASSERTION cannot be used in exception position
  (testing "Simple expressions"
    (is (= (parse "Apache-2.0")                               {:license-id "Apache-2.0"}))
    (is (= (parse "LicenseRef-foo")                           {:license-ref "foo"}))
    (is (= (parse "LicenseRef-foo-bar-blah")                  {:license-ref "foo-bar-blah"}))
    (is (= (parse "DocumentRef-foo:LicenseRef-bar")           {:license-ref "bar" :document-ref "foo"}))
    (is (= (parse "DocumentRef-foo-bar:LicenseRef-blah")      {:license-ref "blah" :document-ref "foo-bar"}))
    (is (= (parse "NONE")                                     {:special-form :none}))
    (is (= (parse "NOASSERTION")                              {:special-form :no-assertion})))
  (testing "Simple expressions - mixed case"  ; Expressions are globally case INsensitive, as of SPDX specification v3.0.2
    (is (= (parse "apache-2.0")                               {:license-id "Apache-2.0"}))
    (is (= (parse "APACHE-2.0")                               {:license-id "Apache-2.0"}))
    (is (= (parse "aPaCHe-2.0")                               {:license-id "Apache-2.0"}))
    (is (= (parse "documentref-foo:licenseref-bar")           {:license-ref "bar" :document-ref "foo"}))
    (is (= (parse "nOnE")                                     {:special-form :none}))
    (is (= (parse "nOaSsErTioN")                              {:special-form :no-assertion})))
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
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-ref "bar" :document-ref "foo"}
                                                               [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]]))
    (is (= (parse "LicenseRef-foo WITH AdditionRef-bar")      {:license-ref "foo" :addition-ref "bar"}))
    (is (= (parse "DocumentRef-foo:LicenseRef-bar WITH DocumentRef-blah:AdditionRef-banana")
                                                              {:document-ref "foo" :license-ref "bar" :addition-document-ref "blah" :addition-ref "banana"}))
    (is (= (parse "NONE WITH Classpath-exception-2.0")        {:special-form :none :license-exception-id "Classpath-exception-2.0"}))  ; Legally nonsensical, though the SPDX ABNF allows it
    ; Expressions are globally case INsensitive, as of SPDX specification v3.0.2
    (is (= (parse "MIT and Apache-2.0")                       [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "MIT or Apache-2.0")                        [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "GPL-2.0 with Classpath-exception-2.0")     {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}))
    (is (= (parse "licenseref-FOO with additionref-BAR")      {:license-ref "FOO" :addition-ref "BAR"}))
    (is (= (parse "documentref-FOO:licenseref-Bar wItH documentref-blah:additionref-bANANA")
                                                              {:document-ref "FOO" :license-ref "Bar" :addition-document-ref "blah" :addition-ref "bANANA"}))
    (is (= (parse "none")                                     {:special-form :none}))
    (is (= (parse "NoAssertion")                              {:special-form :no-assertion}))
    (is (= (parse "none or noassertion")                      [:or {:special-form :no-assertion} {:special-form :none}])))  ; Legally nonsensical, though the SPDX ABNF allows it
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
  (testing "Expressions that exercise license id replacement (deprecated and/or weirdo GNU family replacements)"
    ; 1:1 replacements
    (is (= (parse "AGPL-1.0")                                 {:license-id "AGPL-1.0-only"}))
    (is (= (parse "AGPL-1.0+")                                {:license-id "AGPL-1.0-or-later"}))   ; Note: AGPL-1.0+ is not a listed identifier - it's an expression, but still needs to be replaced
    (is (= (parse "AGPL-3.0")                                 {:license-id "AGPL-3.0-only"}))
    (is (= (parse "AGPL-3.0+")                                {:license-id "AGPL-3.0-or-later"}))   ; Note: AGPL-3.0+ is not a listed identifier - it's an expression, but still needs to be replaced
    (is (= (parse "GPL-1.0")                                  {:license-id "GPL-1.0-only"}))
    (is (= (parse "GPL-1.0+")                                 {:license-id "GPL-1.0-or-later"}))
    (is (= (parse "GPL-2.0")                                  {:license-id "GPL-2.0-only"}))
    (is (= (parse "GPL-2.0+")                                 {:license-id "GPL-2.0-or-later"}))
    (is (= (parse "GPL-3.0")                                  {:license-id "GPL-3.0-only"}))
    (is (= (parse "LGPL-2.0" )                                {:license-id "LGPL-2.0-only"}))
    (is (= (parse "LGPL-2.0+")                                {:license-id "LGPL-2.0-or-later"}))
    (is (= (parse "LGPL-2.1" )                                {:license-id "LGPL-2.1-only"}))
    (is (= (parse "LGPL-2.1+")                                {:license-id "LGPL-2.1-or-later"}))
    (is (= (parse "LGPL-3.0" )                                {:license-id "LGPL-3.0-only"}))
    (is (= (parse "LGPL-3.0+")                                {:license-id "LGPL-3.0-or-later"}))
    (is (= (parse "GFDL-1.1")                                 {:license-id "GFDL-1.1-only"}))
    (is (= (parse "GFDL-1.1+")                                {:license-id "GFDL-1.1-or-later"}))
    (is (= (parse "GFDL-1.2")                                 {:license-id "GFDL-1.2-only"}))
    (is (= (parse "GFDL-1.2+")                                {:license-id "GFDL-1.2-or-later"}))
    (is (= (parse "GFDL-1.3")                                 {:license-id "GFDL-1.3-only"}))
    (is (= (parse "GFDL-1.3+")                                {:license-id "GFDL-1.3-or-later"}))
    (is (= (parse "StandardML-NJ")                            {:license-id "SMLNJ"}))
    (is (= (parse "StandardML-NJ+")                           {:license-id "SMLNJ" :or-later? true}))   ; Note: StandardML-NJ+ is not a listed identifier - it's an expression, but still needs to be replaced, preserving the or-later? flag
    (is (= (parse "BSD-2-Clause-FreeBSD")                     {:license-id "BSD-2-Clause-Views"}))
    (is (= (parse "BSD-2-Clause-NetBSD")                      {:license-id "BSD-2-Clause"}))
    (is (= (parse "bzip2-1.0.5")                              {:license-id "bzip2-1.0.6"}))
    (is (= (parse "LGPL-2.1-only WITH Nokia-Qt-exception-1.1") {:license-id "LGPL-2.1-only" :license-exception-id "Qt-LGPL-exception-1.1"}))
    (is (= (parse "LicenseRef-foo WITH Nokia-Qt-exception-1.1") {:license-ref "foo" :license-exception-id "Qt-LGPL-exception-1.1"}))
    ; 1:2 replacements
    (is (= (parse "Net-SNMP")                                 [:and
                                                               {:license-id "BSD-3-Clause"}
                                                               {:license-id "MIT-CMU"}]))
    (is (= (parse "Net-SNMP+")                                [:and    ; Nonsensical, but confirms that the or-later? flag is preserved during a 1:2 replacement
                                                               {:license-id "BSD-3-Clause" :or-later? true}
                                                               {:license-id "MIT-CMU"      :or-later? true}]))
    ; Cursed expressions with +
    (is (= (parse "GPL-2.0-only+")                            {:license-id "GPL-2.0-or-later"}))
    (is (= (parse "GPL-2.0-or-later+")                        {:license-id "GPL-2.0-or-later"}))
    (is (= (parse "GPL-2.0-only+" {:canonicalise-deprecated-ids? false})      ; This should always be canonicalised, regardless of :canonicalise-deprecated-ids?
                                                              {:license-id "GPL-2.0-or-later"}))
    (is (= (parse "GPL-2.0-or-later+" {:canonicalise-deprecated-ids? false})  ; This should always be canonicalised, regardless of :canonicalise-deprecated-ids?
                                                              {:license-id "GPL-2.0-or-later"}))
    ; Cursed eCos-2.0 and wxWindows cases (these two changed type - license ids replaced by exception ids 😬)
    (is (= (parse "eCos-2.0")                                 {:license-id "GPL-2.0-only"     :license-exception-id "eCos-exception-2.0"}))
    (is (= (parse "eCos-2.0+")                                {:license-id "GPL-2.0-or-later" :license-exception-id "eCos-exception-2.0"}))
    (is (= (parse "eCos-2.0 OR Apache-2.0")                   [:or  {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"     :license-exception-id "eCos-exception-2.0"}]))
    (is (= (parse "eCos-2.0 AND Apache-2.0 AND MIT")          [:and {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"     :license-exception-id "eCos-exception-2.0"} {:license-id "MIT"}]))
    (is (= (parse "Apache-2.0 AND eCos-2.0")                  [:and {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"     :license-exception-id "eCos-exception-2.0"}]))
    (is (= (parse "eCos-2.0 AND Apache-2.0")                  [:and {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"     :license-exception-id "eCos-exception-2.0"}]))
    (is (= (parse "eCos-2.0+ AND Apache-2.0")                 [:and {:license-id "Apache-2.0"} {:license-id "GPL-2.0-or-later" :license-exception-id "eCos-exception-2.0"}]))
    (is (= (parse "eCos-2.0 AND (Apache-2.0)")                [:and {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"     :license-exception-id "eCos-exception-2.0"}]))
    (is (= (parse "GPL-2.0-only WITH Classpath-exception-2.0 AND eCos-2.0")
                                                              [:and
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "eCos-exception-2.0"}]))
    (is (= (parse "GPL-2.0-with-classpath-exception AND eCos-2.0")
                                                              [:and
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "eCos-exception-2.0"}]))
    (is (= (parse "eCos-2.0 WITH Classpath-exception-2.0")    [:and
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "eCos-exception-2.0"}]))
    (is (= (parse "eCos-2.0 WITH eCos-exception-2.0")         {:license-id "GPL-2.0-only" :license-exception-id "eCos-exception-2.0"}))
    (is (= (parse "MIT AND eCos-2.0" {:canonicalise-deprecated-ids? false})
                                                              [:and
                                                               {:license-id "eCos-2.0"}
                                                               {:license-id "MIT"}]))
    (is (= (parse "wxWindows")                                {:license-id "GPL-2.0-only" :license-exception-id "WxWindows-exception-3.1"}))
    (is (= (parse "MIT AND wxWindows")                        [:and {:license-id "GPL-2.0-only" :license-exception-id "WxWindows-exception-3.1"} {:license-id "MIT"}]))
    (is (= (parse "wxWindows AND MIT")                        [:and {:license-id "GPL-2.0-only" :license-exception-id "WxWindows-exception-3.1"} {:license-id "MIT"}]))
    (is (= (parse "wxWindows WITH WxWindows-exception-3.1")   {:license-id "GPL-2.0-only" :license-exception-id "WxWindows-exception-3.1"}))
    (is (= (parse "BSD-2-Clause AND wxWindows" {:canonicalise-deprecated-ids? false})
                                                              [:and
                                                               {:license-id "BSD-2-Clause"}
                                                               {:license-id "wxWindows"}]))
    (is (= (parse "eCos-2.0 AND wxWindows")                   [:and
                                                               {:license-id "GPL-2.0-only" :license-exception-id "eCos-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "WxWindows-exception-3.1"}]))
    (is (= (parse "wxWindows AND eCos-2.0")                   [:and
                                                               {:license-id "GPL-2.0-only" :license-exception-id "eCos-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "WxWindows-exception-3.1"}]))
    ; Cursed "double license exception" cases
    (is (= (parse "GPL-2.0-with-classpath-exception WITH Classpath-exception-2.0")
                                                              {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}))
    (is (= (parse "GPL-2.0-with-GCC-exception WITH Classpath-exception-2.0")
                                                              [:and
                                                               {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "GCC-exception-2.0"}]))
    (is (= (parse "GPL-2.0-with-GCC-exception+ WITH Classpath-exception-2.0")
                                                              [:and
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-id "GPL-2.0-or-later" :license-exception-id "GCC-exception-2.0"}]))
    (is (= (parse "Net-SNMP WITH Classpath-exception-2.0")    [:and
                                                               {:license-id "BSD-3-Clause" :license-exception-id "Classpath-exception-2.0"}
                                                               {:license-id "MIT-CMU"      :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "eCos-2.0 WITH GCC-exception-2.0 AND Apache-2.0")
                                                              [:and
                                                               {:license-id "Apache-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "eCos-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "GCC-exception-2.0"}]))
    (is (= (parse "eCos-2.0 WITH GCC-exception-2.0 AND MIT WITH WxWindows-exception-3.1")
                                                              [:and
                                                               {:license-id "GPL-2.0-only" :license-exception-id "eCos-exception-2.0"}
                                                               {:license-id "GPL-2.0-only" :license-exception-id "GCC-exception-2.0"}
                                                               {:license-id "MIT"          :license-exception-id "WxWindows-exception-3.1"}])))
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
    (is (= (parse "(Apache-2.0 OR MIT) AND (MIT OR Apache-2.0)") [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
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
                                                              {:document-ref "foo" :license-ref "bar"}))
    (is (= (parse "NONE OR NONE")                             {:special-form :none}))           ; Legally nonsensical, though the SPDX ABNF allows it
    (is (= (parse "NOASSERTION OR NOASSERTION")               {:special-form :no-assertion})))  ; Legally nonsensical, though the SPDX ABNF allows it
  (testing "Expressions that exercise sorting"
    (is (= (parse "Apache-2.0 OR MIT")                        [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "MIT OR Apache-2.0")                        [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "MIT OR Apache-2.0" {:sort-licenses? true}) [:or {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (parse "MIT OR Apache-2.0" {:sort-licenses? false}) [:or {:license-id "MIT"} {:license-id "Apache-2.0"}]))
    (is (= (parse "EPL-2.0 OR EPL-1.0")                       [:or {:license-id "EPL-1.0"} {:license-id "EPL-2.0"}]))                                     ; Sorting by version
    (is (= (parse "MIT AND dtoa AND CDDL-1.0")                [:and {:license-id "CDDL-1.0"} {:license-id "dtoa"} {:license-id "MIT"}]))                  ; Case-insensitive sorting
    (is (= (parse "Apache-2.0+ OR Apache-2.0")                [:or {:license-id "Apache-2.0"} {:license-id "Apache-2.0" :or-later? true}]))               ; Licenses with or-later flag after licenses alone (even when same license id)
    (is (= (parse "GPL-2.0-only WITH Classpath-exception-2.0 OR GPL-2.0-only")                                                                            ; Licenses with exceptions after licenses alone (even when same license id)
                                                              [:or {:license-id "GPL-2.0-only"} {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "GPL-2.0-only WITH AdditionRef-foo OR GPL-2.0-only")                                                                                    ; Licenses with AdditionRefs after licenses alone (even when same license id)
                                                              [:or {:license-id "GPL-2.0-only"} {:license-id "GPL-2.0-only" :addition-ref "foo"}]))
    (is (= (parse "LicenseRef-foo WITH Classpath-exception-2.0 OR LicenseRef-foo")                                                                        ; LicenseRefs with exceptions after LicenseRefs alone (even when same LicenseRef)
                                                              [:or {:license-ref "foo"} {:license-ref "foo" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "LicenseRef-foo WITH AdditionRef-foo OR LicenseRef-foo")                                                                                ; LicenseRefs with AdditionRefs after LicenseRefs alone (even when same LicenseRef)
                                                              [:or {:license-ref "foo"} {:license-ref "foo" :addition-ref "foo"}]))
    (is (= (parse "LicenseRef-foo OR MIT")                    [:or {:license-id "MIT"} {:license-ref "foo"}]))                                            ; LicenseRefs after licenses
    (is (= (parse "(GPL-2.0-only OR Apache-2.0) AND MIT")     [:and {:license-id "MIT"} [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"}]]))  ; Sub-clauses after licenses
    (is (= (parse "(GPL-2.0-only OR Apache-2.0) AND LicenseRef-foo")                                                                                      ; Sub-clauses after LicenseRefs
                                                              [:and {:license-ref "foo"} [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0-only"}]]))
    (is (= (parse "MIT WITH AdditionRef-foo AND MIT WITH AdditionRef-FOO")                                                                                ; Case *in*sensitive license id sorting, with case sensitive AdditionRef sorting (refs are case-preserving, but case *in*sensitive during comparison)
                                                              [:and {:license-id "MIT" :addition-ref "FOO"} {:license-id "MIT" :addition-ref "foo"}]))
    (is (= (parse "GPL-2.0-only WITH AdditionRef-foo AND GPL-2.0-only WITH Classpath-exception-2.0")                                                      ; AdditionRefs sort after license exceptions
                                                              [:and {:license-id "GPL-2.0-only" :license-exception-id "Classpath-exception-2.0"} {:license-id "GPL-2.0-only" :addition-ref "foo"}]))
    (is (= (parse "LicenseRef-foo WITH AdditionRef-foo AND LicenseRef-foo WITH Classpath-exception-2.0")                                                  ; AdditionRefs sort after license exceptions (LicenseRef variant)
                                                              [:and {:license-ref "foo" :license-exception-id "Classpath-exception-2.0"} {:license-ref "foo" :addition-ref "foo"}]))
    (is (= (parse "NONE AND Apache-2.0")                      [:and {:license-id "Apache-2.0"} {:special-form :none}]))                                   ; Special forms after licenses
    (is (= (parse "NOASSERTION AND Apache-2.0")               [:and {:license-id "Apache-2.0"} {:special-form :no-assertion}]))
    (is (= (parse "NONE AND LicenseRef-foo")                  [:and {:license-ref "foo"} {:special-form :none}]))))                                       ; Special forms after LicenseRefs

(deftest uncanonicalised-parse-tests
  (testing "Simple expressions - canonicalisation"
    (is (= (parse "AGPL-1.0"                               {:canonicalise-deprecated-ids? true}) {:license-id "AGPL-1.0-only"}))
    (is (= (parse "GPL-2.0"                                {:canonicalise-deprecated-ids? true}) {:license-id "GPL-2.0-only"}))
    (is (= (parse "StandardML-NJ"                          {:canonicalise-deprecated-ids? true}) {:license-id "SMLNJ"}))
    (is (= (parse "Apache-2.0 WITH Nokia-Qt-exception-1.1" {:canonicalise-deprecated-ids? true}) {:license-id "Apache-2.0" :license-exception-id "Qt-LGPL-exception-1.1"})))
  (testing "Simple expressions - no canonicalisation"
    (is (= (parse "AGPL-1.0"                               {:canonicalise-deprecated-ids? false}) {:license-id "AGPL-1.0"}))
    (is (= (parse "GPL-2.0"                                {:canonicalise-deprecated-ids? false}) {:license-id "GPL-2.0"}))
    (is (= (parse "StandardML-NJ"                          {:canonicalise-deprecated-ids? false}) {:license-id "StandardML-NJ"}))
    (is (= (parse "Apache-2.0 WITH Nokia-Qt-exception-1.1" {:canonicalise-deprecated-ids? false}) {:license-id "Apache-2.0" :license-exception-id "Nokia-Qt-exception-1.1"})))
  (testing "Compound expressions"
    (is (= (parse "GPL-2.0+" {:canonicalise-deprecated-ids? false})
           {:license-id "GPL-2.0" :or-later? true}))
    (is (= (parse "GPL-2.0-only+" {:canonicalise-deprecated-ids? false})
           {:license-id "GPL-2.0-or-later"}))  ; This is a mandatory replacement, not controllable via the :canonicalise-deprecated-ids? flag
    (is (= (parse "Apache-2.0 OR GPL-2.0" {:canonicalise-deprecated-ids? false})
           [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0"}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0+" {:canonicalise-deprecated-ids? false})
           [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0" :or-later? true}]))
    (is (= (parse "Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0" {:canonicalise-deprecated-ids? false})
           [:or
            {:license-id "Apache-2.0"}
            {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)" {:canonicalise-deprecated-ids? false})
           [:or
            {:license-id "Apache-2.0"}
            {:license-id "GPL-2.0" :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "APACHE-2.0 OR (((((GPL-2.0+ WITH CLASSPATH-EXCEPTION-2.0)))))" {:canonicalise-deprecated-ids? false})
           [:or
            {:license-id "Apache-2.0"}
            {:license-id "GPL-2.0"
             :or-later? true
             :license-exception-id "Classpath-exception-2.0"}]))
    (is (= (parse "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar" {:canonicalise-deprecated-ids? false})
           [:or
            {:license-id "GPL-2.0" :or-later? true :license-exception-id "Classpath-exception-2.0"}
            {:license-ref "bar" :document-ref "foo"}
            [:and {:license-id "Apache-2.0"} {:license-id "MIT"}]]))
    (is (= (parse "GPL-2.0-with-GCC-exception WITH Classpath-exception-2.0" {:canonicalise-deprecated-ids? false})
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
           "DocumentRef-foo:LicenseRef-bar WITH DocumentRef-blah:AdditionRef-banana"))
    (is (= (unparse {:special-form :none})                                                   "NONE"))
    (is (= (unparse {:special-form :no-assertion})                                           "NOASSERTION")))
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
           "(Apache-2.0 AND MIT) OR GPL-2.0+ WITH Classpath-exception-2.0 OR DocumentRef-foo:LicenseRef-bar"))
    (is (= (unparse [:and {:license-id "MIT"} {:special-form :none}])                        "MIT AND NONE"))
    (is (= (unparse [:and {:license-id "MIT"} {:special-form :no-assertion}])                "MIT AND NOASSERTION")))
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
                                                     "GPL-2.0-or-later WITH Classpath-exception-2.0 OR (Apache-2.0+ AND MIT) OR (BSD-2-Clause AND DocumentRef-bar:LicenseRef-foo)"))
    (is (= (unparse (parse "NONE"))                  "NONE"))
    (is (= (unparse (parse "NOASSERTION"))           "NOASSERTION"))
    (is (= (unparse (parse "NONE AND MIT"))          "MIT AND NONE"))
    (is (= (unparse (parse "NOASSERTION AND MIT"))   "MIT AND NOASSERTION"))))

; Note: we keep these short(ish), as the parser is far more extensively exercised by parse-tests and unparse-tests
; Precedence rule tests are only here however, as they're less cumbersome to test using canonicalise
(deftest canonicalise-tests
  (testing "Nil, blank, etc."
    (is (nil? (canonicalise nil)))
    (is (nil? (canonicalise "")))
    (is (nil? (canonicalise "  ")))
    (is (nil? (canonicalise "\n\t"))))
  (testing "Invalid expressions"
    (is (nil? (canonicalise "AND")))
    (is (nil? (canonicalise "THIS-IS-NOT-A-LICENSE-ID")))
    (is (nil? (canonicalise "DocumentRef-foo")))
    (is (nil? (canonicalise "LicenseRef-this:is:invalid")))
    (is (nil? (canonicalise "((BSD-2-Clause")))
    (is (nil? (canonicalise "Classpath-exception-2.0"))))
  (testing "Simple expressions"
    (is (= (canonicalise "Apache-2.0")                     "Apache-2.0"))
    (is (= (canonicalise "aPaCHe-2.0")                     "Apache-2.0"))
    (is (= (canonicalise "((bsd-4-clause))")               "BSD-4-Clause"))
    (is (= (canonicalise "LGPL-3.0")                       "LGPL-3.0-only"))
    (is (= (canonicalise "LGPL-3.0+")                      "LGPL-3.0-or-later"))
    (is (= (canonicalise "LGPL-3.0-or-later")              "LGPL-3.0-or-later"))
    (is (= (canonicalise "LicenseRef-foo")                 "LicenseRef-foo"))
    (is (= (canonicalise "DocumentRef-foo:LicenseRef-bar") "DocumentRef-foo:LicenseRef-bar"))
    (is (= (canonicalise "NONE")                           "NONE"))
    (is (= (canonicalise "NOASSERTION")                    "NOASSERTION"))
    ; Expressions are globally case INsensitive, as of SPDX specification v3.0.2
    (is (= (canonicalise "licenseref-foo")                 "LicenseRef-foo"))
    (is (= (canonicalise "DOCUMENTREF-foo:LICENSEREF-bar") "DocumentRef-foo:LicenseRef-bar"))
    (is (= (canonicalise "nOnE")                           "NONE"))
    (is (= (canonicalise "NoAssertion")                    "NOASSERTION")))
  (testing "Compound expressions"
    (is (= (canonicalise "MIT and AGPL-3.0")                                                        "AGPL-3.0-only AND MIT"))
    (is (= (canonicalise "(GPL-2.0 WITH Classpath-exception-2.0)")                                  "GPL-2.0-only WITH Classpath-exception-2.0"))
    (is (= (canonicalise "BSD-2-Clause AND MIT or GPL-2.0+ WITH Classpath-exception-2.0")           "GPL-2.0-or-later WITH Classpath-exception-2.0 OR (BSD-2-Clause AND MIT)"))
    (is (= (canonicalise "(BSD-2-Clause AND MIT) Or GPL-2.0+ WITH Classpath-exception-2.0")         "GPL-2.0-or-later WITH Classpath-exception-2.0 OR (BSD-2-Clause AND MIT)"))
    (is (= (canonicalise "GPL-2.0-with-GCC-exception WiTh Classpath-exception-2.0")                 "GPL-2.0-only WITH Classpath-exception-2.0 AND GPL-2.0-only WITH GCC-exception-2.0"))
    (is (= (canonicalise "LicenseRef-foo WITH Classpath-exception-2.0")                             "LicenseRef-foo WITH Classpath-exception-2.0"))
    (is (= (canonicalise "Apache-2.0 WITH AdditionRef-foo")                                         "Apache-2.0 WITH AdditionRef-foo"))
    (is (= (canonicalise "Apache-2.0 WITH additionref-foo")                                         "Apache-2.0 WITH AdditionRef-foo"))
    (is (= (canonicalise "LicenseRef-foo with AdditionRef-blah")                                    "LicenseRef-foo WITH AdditionRef-blah"))
    (is (= (canonicalise "DocumentRef-foo:LicenseRef-bar wItH DocumentRef-blah:AdditionRef-banana") "DocumentRef-foo:LicenseRef-bar WITH DocumentRef-blah:AdditionRef-banana"))
    ; Expressions are globally case INsensitive, as of SPDX specification v3.0.2
    (is (= (canonicalise "licenseref-foo with additionref-blah")                                    "LicenseRef-foo WITH AdditionRef-blah"))
    (is (= (canonicalise "documentref-foo:licenseref-bar wItH documentref-blah:additionref-banana") "DocumentRef-foo:LicenseRef-bar WITH DocumentRef-blah:AdditionRef-banana")))
  (testing "Precedence rules"
    (is (= (canonicalise "Apache-2.0 OR  (MIT or  BSD-3-Clause)") "Apache-2.0 OR BSD-3-Clause OR MIT"))
    (is (= (canonicalise "Apache-2.0 and (MIT AND BSD-3-Clause)") "Apache-2.0 AND BSD-3-Clause AND MIT"))
    (is (= (canonicalise "((((((Apache-2.0)))))) AND (MIT and BSD-3-Clause)")
           "Apache-2.0 AND BSD-3-Clause AND MIT"))
    (is (= (canonicalise "(Apache-2.0 or  MIT) or  BSD-3-Clause") "Apache-2.0 OR BSD-3-Clause OR MIT"))
    (is (= (canonicalise "(Apache-2.0 and MIT) and BSD-3-Clause") "Apache-2.0 AND BSD-3-Clause AND MIT"))
    (is (= (canonicalise "Apache-2.0 oR  MIT aNd BSD-3-Clause")   "Apache-2.0 OR (BSD-3-Clause AND MIT)"))
    (is (= (canonicalise "Apache-2.0 AnD MIT Or  BSD-3-Clause")   "BSD-3-Clause OR (Apache-2.0 AND MIT)"))
    (is (= (canonicalise "Apache-2.0 or  MIT and BSD-3-Clause or Unlicense")
           "Apache-2.0 OR Unlicense OR (BSD-3-Clause AND MIT)"))
    (is (= (canonicalise "Apache-2.0 AND MIT OR BSD-3-Clause and Unlicense")
           "(Apache-2.0 AND MIT) OR (BSD-3-Clause AND Unlicense)"))
    (is (= (canonicalise "Apache-2.0 OR (MIT and BSD-3-Clause OR Unlicense)")
           "Apache-2.0 OR Unlicense OR (BSD-3-Clause AND MIT)"))
    (is (= (canonicalise "mit or bsd-3-clause AND apache-2.0 and beerware OR epl-2.0 and mpl-2.0 OR unlicense and lgpl-3.0 OR wtfpl or glwtpl OR hippocratic-2.1")
           "GLWTPL OR Hippocratic-2.1 OR MIT OR WTFPL OR (EPL-2.0 AND MPL-2.0) OR (LGPL-3.0-only AND Unlicense) OR (Apache-2.0 AND Beerware AND BSD-3-Clause)"))
    (is (= (canonicalise "MIT or (BSD-3-Clause OR (Apache-2.0 OR (Beerware OR (EPL-2.0 OR (MPL-2.0 OR (Unlicense OR (LGPL-3.0-only OR (WTFPL OR (GLWTPL OR (Hippocratic-2.1))))))))))")
           "Apache-2.0 OR Beerware OR BSD-3-Clause OR EPL-2.0 OR GLWTPL OR Hippocratic-2.1 OR LGPL-3.0-only OR MIT OR MPL-2.0 OR Unlicense OR WTFPL"))
    (is (= (canonicalise "MIT and (BSD-3-Clause AND (Apache-2.0 and (Beerware AND (EPL-2.0 and (MPL-2.0 AND (Unlicense and (LGPL-3.0-only AND (WTFPL and (GLWTPL AND (Hippocratic-2.1))))))))))")
           "Apache-2.0 AND Beerware AND BSD-3-Clause AND EPL-2.0 AND GLWTPL AND Hippocratic-2.1 AND LGPL-3.0-only AND MIT AND MPL-2.0 AND Unlicense AND WTFPL"))
    (is (= (canonicalise "MIT and (BSD-3-Clause or (Apache-2.0 and (Beerware or (EPL-2.0 and (MPL-2.0 or (Unlicense and (LGPL-3.0-only or (WTFPL and (GLWTPL or Hippocratic-2.1)))))))))")
           "MIT AND (BSD-3-Clause OR (Apache-2.0 AND (Beerware OR (EPL-2.0 AND (MPL-2.0 OR (Unlicense AND (LGPL-3.0-only OR (WTFPL AND (GLWTPL OR Hippocratic-2.1)))))))))"))
    (is (= (canonicalise "MIT OR (BSD-3-Clause AND (Apache-2.0 OR (Beerware AND (EPL-2.0 OR (MPL-2.0 AND (Unlicense OR (LGPL-3.0-only AND (WTFPL OR (GLWTPL AND (Hippocratic-2.1))))))))))")
           "MIT OR (BSD-3-Clause AND (Apache-2.0 OR (Beerware AND (EPL-2.0 OR (MPL-2.0 AND (Unlicense OR (LGPL-3.0-only AND (WTFPL OR (GLWTPL AND Hippocratic-2.1)))))))))")))
  (testing "Collapsing redundant expressions"
    (is (= (canonicalise "Apache-2.0 OR Apache-2.0")              "Apache-2.0"))
    (is (= (canonicalise "Apache-2.0 OR (Apache-2.0 AND (Apache-2.0 AND Apache-2.0) OR Apache-2.0)")
           "Apache-2.0")))
  (testing "Sorting of licenses within the parse tree"
    (is (= (canonicalise "Apache-2.0 OR MIT")                     (canonicalise "MIT OR Apache-2.0")))
    (is (= (canonicalise "apache-2.0 or mit")                     (canonicalise "MIT OR APACHE-2.0")))))

; Note: we keep these short, as the parser is far more extensively exercised by parse-tests
(deftest valid?-tests
  (testing "Nil, empty, etc."
    (is (not (valid? nil)))
    (is (not (valid? ""))))
  (testing "Invalid expressions"
    (is (not (valid? "+")))
    (is (not (valid? "AND")))
    (is (not (valid? "Apache")))
    (is (not (valid? "Classpath-exception-2.0"))))
  (testing "Valid expressions"
    (is (valid? "Apache-2.0"))
    (is (valid? "apache-2.0"))
    (is (valid? "GPL-2.0+"))
    (is (valid? "LicenseRef-foo"))
    (is (valid? "DocumentRef-foo:LicenseRef-bar"))
    (is (valid? "NONE"))
    (is (valid? "NOASSERTION"))
    (is (valid? "GPL-2.0 WITH Classpath-exception-2.0"))
    (is (valid? "\tapache-2.0 OR\n( gpl-2.0\tWITH\nclasspath-exception-2.0\n\t\n\t)"))
    (is (valid? "(APACHE-2.0 AND MIT) OR (((GPL-2.0 WITH CLASSPATH-EXCEPTION-2.0)))"))
    (is (valid? "MIT or Apache-2.0"))))  ; Expressions are globally case INsensitive, as of SPDX specification v3.0.2

(deftest simple?-tests
  (testing "Nil, empty, etc."
    (is (nil? (simple? nil)))
    (is (nil? (simple? ""))))
  (testing "Invalid expressions"
    (is (nil? (simple? "+")))
    (is (nil? (simple? "AND")))
    (is (nil? (simple? "Apache")))
    (is (nil? (simple? "Classpath-exception-2.0"))))
  (testing "Valid expressions - simple"
    (is (true? (simple? "Apache-2.0")))
    (is (true? (simple? "LicenseRef-foo")))
    (is (true? (simple? "NONE")))
    (is (true? (simple? "GPL-2.0-or-later WITH Classpath-exception-2.0")))
    (is (true? (simple? "DocumentRef-foo:LicenseRef-foo WITH DocumentRef-bar:AdditionRef-bar"))))
  (testing "Valid expressions - compound"
    (is (false? (simple? "Apache-2.0 AND MIT")))
    (is (false? (simple? "Apache-2.0 and NONE")))
    (is (false? (simple? "GPL-2.0-or-later WITH Classpath-exception-2.0 OR EPL-1.0")))
    (is (false? (simple? "MIT or Apache-2.0")))  ; Expressions are globally case INsensitive, as of SPDX specification v3.0.2
    (is (false? (simple? "Apache-2.0 or noassertion")))))

(deftest compound?-tests
  (testing "Nil, empty, etc."
    (is (nil? (compound? nil)))
    (is (nil? (compound? ""))))
  (testing "Invalid expressions"
    (is (nil? (compound? "+")))
    (is (nil? (compound? "AND")))
    (is (nil? (compound? "Apache")))
    (is (nil? (compound? "Classpath-exception-2.0"))))
  (testing "Valid expressions - simple"
    (is (false? (compound? "Apache-2.0")))
    (is (false? (compound? "LicenseRef-foo")))
    (is (false? (compound? "NOASSERTION")))
    (is (false? (compound? "GPL-2.0-or-later WITH Classpath-exception-2.0")))
    (is (false? (compound? "DocumentRef-foo:LicenseRef-foo WITH DocumentRef-bar:AdditionRef-bar"))))
  (testing "Valid expressions - compound"
    (is (true? (compound? "Apache-2.0 AND MIT")))
    (is (true? (compound? "GPL-2.0-or-later WITH Classpath-exception-2.0 OR EPL-1.0")))
    (is (true? (compound? "MIT or Apache-2.0")))  ; Expressions are globally case INsensitive, as of SPDX specification v3.0.2
    (is (true? (compound? "Apache-2.0 or none")))))

(deftest extract-ids-tests
  (testing "Nil"
    (is (nil? (extract-ids nil))))
  (testing "Simple parse results"
    (is (= (extract-ids {:license-id "Apache-2.0"})                               #{"Apache-2.0"}))
    (is (= (extract-ids [:or {:license-id "Apache-2.0"} {:license-id "GPL-2.0"}]) #{"Apache-2.0" "GPL-2.0"})))
  (testing "Include or later"
    (is (= (extract-ids {:license-id "GPL-2.0" :or-later? true} {:include-or-later? false}) #{"GPL-2.0"}))
    (is (= (extract-ids {:license-id "GPL-2.0" :or-later? true} {:include-or-later? true})  #{"GPL-2.0+"})))
  (testing "LicenseRefs and AdditionRefs"
    (is (= (extract-ids {:license-ref "foo"})                                                                          #{"LicenseRef-foo"}))
    (is (= (extract-ids {:document-ref "foo" :license-ref "bar"})                                                      #{"DocumentRef-foo:LicenseRef-bar"}))
    (is (= (extract-ids {:license-ref "foo" :addition-ref "bar"})                                                      #{"LicenseRef-foo" "AdditionRef-bar"}))
    (is (= (extract-ids {:document-ref "foo" :license-ref "bar" :addition-document-ref "blah" :addition-ref "banana"}) #{"DocumentRef-foo:LicenseRef-bar" "DocumentRef-blah:AdditionRef-banana"})))
  (testing "Special forms"
    (is (= (extract-ids {:special-form :none})         #{"NONE"}))
    (is (= (extract-ids {:special-form :no-assertion}) #{"NOASSERTION"})))
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
  (testing "Walk functions"  ;Note: these walk functions are _not_ general purpose - they will fail on other valid parse trees
    (is (= (walk {:op-fn      name}                      (parse "MIT OR Apache-2.0"))   ["or" {:license-id "Apache-2.0"} {:license-id "MIT"}]))
    (is (= (walk {:license-fn :license-id}               (parse "MIT OR Apache-2.0"))   [:or "Apache-2.0" "MIT"]))
    (is (= (walk {:license-fn #(name (:special-form %))} (parse "NONE OR NOASSERTION")) [:or "no-assertion" "none"]))
    (is (= (walk {:group-fn   #(count %2)}               (parse "MIT OR Apache-2.0"))   3))))
