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

(ns spdx.matching-test
  (:require [clojure.test    :refer [deftest testing is]]
            [spdx.test-utils :refer [run-all-slow-tests?]]
            [spdx.matching   :refer [text-is-license? text-is-exception? text-contains-license? text-contains-exception?
                                     texts-equivalent-licenses? texts-equivalent-exceptions? licenses-within-text exceptions-within-text]]))

; Single license texts
(def apache-2-text                   (slurp "https://www.apache.org/licenses/LICENSE-2.0.txt"))
(def gpl-3-text                      (slurp "https://www.gnu.org/licenses/gpl-3.0.txt"))
(def cc-by-4-text                    (slurp "https://creativecommons.org/licenses/by/4.0/legalcode.txt"))
(def mpl-2-text                      (slurp "https://www.mozilla.org/media/MPL/2.0/index.txt"))
(def clj-spdx-license                (slurp "https://raw.githubusercontent.com/pmonks/clj-spdx/main/LICENSE"))                 ; Apache-2.0
(def commonmark-java-license         (slurp "https://raw.githubusercontent.com/commonmark/commonmark-java/main/LICENSE.txt"))  ; BSD-2-Clause

; Dual license texts
(def apache-2-gpl-3-text             (str "THIS WORK IS DUAL-LICENSED, UNDER:\n\n" apache-2-text "\n\nOR, AT YOUR DISCRETION:\n\n" gpl-3-text))
;(def jffi-text                       (slurp "https://raw.githubusercontent.com/jnr/jffi/master/LICENSE"))                      ; Apache-2.0 OR LGPL-3.0+, but blocked on https://github.com/jnr/jffi/issues/141
(def javamail-license                (slurp "https://raw.githubusercontent.com/javaee/javamail/master/LICENSE.txt"))           ; CDDL-1.1 OR GPL-2.0 WITH Classpath-exception-2.0

; Exception texts
(def classpath-2-text                (slurp "./test/data/Classpath-exception-2.0.txt"))

; Dual license plus exception text
(def apache-2-gpl-3-classpath-2-text (str apache-2-gpl-3-text "\n\n" classpath-2-text))

; Note: a lot of these tests are very lightweight, since they would otherwise duplicate unit tests that already exist in the underlying Java library

(deftest text-is-license?-tests
  (testing "nil, empty string"
    (is (false? (text-is-license? nil nil)))
    (is (false? (text-is-license? "" nil)))
    (is (false? (text-is-license? nil "")))
    (is (false? (text-is-license? "" "")))
    (is (false? (text-is-license? apache-2-text nil)))
    (is (false? (text-is-license? apache-2-text "")))
    (is (false? (text-is-license? nil "Apache-2.0")))
    (is (false? (text-is-license? "" "Apache-2.0"))))
  (testing "Invalid id"
    (is (false? (text-is-license? apache-2-text "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Matching texts & licenses"
    (is (true?  (text-is-license? apache-2-text           "Apache-2.0")))
    (is (true?  (text-is-license? gpl-3-text              "GPL-3.0")))
;    (is (true?  (text-is-license? cc-by-4-text            "CC-BY-4.0")))    ; Blocked on https://github.com/spdx/Spdx-Java-Library/issues/164
    (is (true?  (text-is-license? mpl-2-text              "MPL-2.0")))
    (is (true?  (text-is-license? clj-spdx-license        "Apache-2.0")))
    (is (true?  (text-is-license? commonmark-java-license "BSD-2-Clause")))))

(deftest text-is-exception?-tests
  (testing "nil, empty string"
    (is (false? (text-is-exception? nil nil)))
    (is (false? (text-is-exception? "" nil)))
    (is (false? (text-is-exception? nil "")))
    (is (false? (text-is-exception? "" "")))
    (is (false? (text-is-exception? classpath-2-text nil)))
    (is (false? (text-is-exception? classpath-2-text "")))
    (is (false? (text-is-exception? nil "Classpath-exception-2.0")))
    (is (false? (text-is-exception? "" "Classpath-exception-2.0"))))
  (testing "Invalid id"
    (is (false? (text-is-exception? classpath-2-text "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Matching texts & exceptions"
    (is (true?  (text-is-exception? classpath-2-text "Classpath-exception-2.0")))))

(deftest text-contains-license?-tests
  (testing "nil, empty string"
    (is (false? (text-contains-license? nil nil)))
    (is (false? (text-contains-license? "" nil)))
    (is (false? (text-contains-license? nil "")))
    (is (false? (text-contains-license? "" "")))
    (is (false? (text-contains-license? apache-2-text nil)))
    (is (false? (text-contains-license? apache-2-text "")))
    (is (false? (text-contains-license? nil "Apache-2.0")))
    (is (false? (text-contains-license? "" "Apache-2.0"))))
  (testing "Invalid id"
    (is (false? (text-contains-license? apache-2-text "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Exactly matching texts & licenses"
    (is (true?  (text-contains-license? apache-2-text           "Apache-2.0")))
    (is (true?  (text-contains-license? gpl-3-text              "GPL-3.0")))
;    (is (true?  (text-contains-license? cc-by-4-text            "CC-BY-4.0")))    ; Blocked on https://github.com/spdx/Spdx-Java-Library/issues/164
    (is (true?  (text-contains-license? mpl-2-text              "MPL-2.0")))
    (is (true?  (text-contains-license? clj-spdx-license        "Apache-2.0")))
    (is (true?  (text-contains-license? commonmark-java-license "BSD-2-Clause"))))
  (testing "Matching larger texts & licenses"
    (is (true?  (text-contains-license? (str "ABCD\n" apache-2-text           "\nEFGH") "Apache-2.0")))
    (is (true?  (text-contains-license? (str "ABCD\n" gpl-3-text              "\nEFGH") "GPL-3.0")))
;    (is (true?  (text-contains-license? (str "ABCD\n" cc-by-4-text            "\nEFGH") "CC-BY-4.0")))    ; Blocked on https://github.com/spdx/Spdx-Java-Library/issues/164
    (is (true?  (text-contains-license? (str "ABCD\n" mpl-2-text              "\nEFGH") "MPL-2.0")))
    (is (true?  (text-contains-license? (str "ABCD\n" clj-spdx-license        "\nEFGH") "Apache-2.0")))
;    (is (true?  (text-contains-license? jffi-text                                       "Apache-2.0")))           ; Blocked on https://github.com/jnr/jffi/issues/141
;    (is (true?  (text-contains-license? jffi-text                                       "LGPL-3.0-or-later")))    ; Blocked on https://github.com/jnr/jffi/issues/141
    (is (true?  (text-contains-license? (str "ABCD\n" commonmark-java-license "\nEFGH") "BSD-2-Clause")))))

(deftest text-contains-exception?-tests
  (testing "nil, empty string"
    (is (false? (text-contains-exception? nil nil)))
    (is (false? (text-contains-exception? "" nil)))
    (is (false? (text-contains-exception? nil "")))
    (is (false? (text-contains-exception? "" "")))
    (is (false? (text-contains-exception? classpath-2-text nil)))
    (is (false? (text-contains-exception? classpath-2-text "")))
    (is (false? (text-contains-exception? nil "Classpath-exception-2.0")))
    (is (false? (text-contains-exception? "" "Classpath-exception-2.0"))))
  (testing "Invalid id"
    (is (false? (text-contains-exception? classpath-2-text "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Exactly matching texts & exceptions"
    (is (true?  (text-contains-exception? classpath-2-text "Classpath-exception-2.0"))))
  (testing "Matching larger texts & exceptions"
    (is (true?  (text-contains-exception? (str "ABCD\n" classpath-2-text "\nEFGH") "Classpath-exception-2.0")))))

(deftest texts-equivalent-licenses?-tests
  (testing "nil, empty string"
    (is (true?  (texts-equivalent-licenses? nil nil)))
    (is (false? (texts-equivalent-licenses? "" nil)))
    (is (false? (texts-equivalent-licenses? nil "")))
    (is (true?  (texts-equivalent-licenses? "" "")))
    (is (false? (texts-equivalent-licenses? apache-2-text nil)))
    (is (false? (texts-equivalent-licenses? apache-2-text "")))
    (is (false? (texts-equivalent-licenses? nil clj-spdx-license)))
    (is (false? (texts-equivalent-licenses? "" clj-spdx-license))))
  (testing "Equivalent texts"
    (is (true?  (texts-equivalent-licenses? apache-2-text clj-spdx-license)))))

(deftest texts-equivalent-exceptions?-tests
  (testing "nil, empty string"
    (is (true?  (texts-equivalent-exceptions? nil nil)))
    (is (false? (texts-equivalent-exceptions? "" nil)))
    (is (false? (texts-equivalent-exceptions? nil "")))
    (is (true?  (texts-equivalent-exceptions? "" "")))
    (is (false? (texts-equivalent-exceptions? classpath-2-text nil)))
    (is (false? (texts-equivalent-exceptions? classpath-2-text "")))
    (is (false? (texts-equivalent-exceptions? nil classpath-2-text)))
    (is (false? (texts-equivalent-exceptions? "" classpath-2-text))))
  (testing "Equivalent texts"
    (is (true?  (texts-equivalent-exceptions? classpath-2-text (str "\n\n" classpath-2-text "\n\n"))))))

(deftest licenses-within-text-tests
  (testing "nil, empty string"
    (is (nil? (licenses-within-text nil)))
    (is (nil? (licenses-within-text ""))))
  (testing "Text without a valid license"
    (is (nil? (licenses-within-text "ABCDEFG"))))
  (testing "Texts with single licenses and only check for that license"
    (is (= (licenses-within-text apache-2-text #{"Apache-2.0"}) #{"Apache-2.0"})))
  (when run-all-slow-tests?
    (testing "Texts with single licenses and nothing else"
      (is (= (licenses-within-text apache-2-text)           #{"Apache-2.0"}))
      (is (= (licenses-within-text gpl-3-text)              #{"GPL-3.0-only" "GPL-3.0+" "GPL-3.0-or-later" "GPL-3.0"}))
;      (is (= (licenses-within-text cc-by-4-text)            #{"CC-BY-4.0"}))    ; Blocked on https://github.com/spdx/Spdx-Java-Library/issues/164
      (is (= (licenses-within-text mpl-2-text)              #{"MPL-2.0-no-copyleft-exception" "MPL-2.0"}))
      (is (= (licenses-within-text clj-spdx-license)        #{"Apache-2.0"}))
      (is (= (licenses-within-text commonmark-java-license) #{"BSD-2-Clause"})))
    (testing "Texts with single licenses and other text"
      (is (= (licenses-within-text (str "ABCD\n" apache-2-text           "\nEFGH")) #{"Apache-2.0"}))
      (is (= (licenses-within-text (str "ABCD\n" gpl-3-text              "\nEFGH")) #{"GPL-3.0-only" "GPL-3.0+" "GPL-3.0-or-later" "GPL-3.0"}))
;      (is (= (licenses-within-text (str "ABCD\n" cc-by-4-text            "\nEFGH")) #{"CC-BY-4.0"}))    ; Blocked on https://github.com/spdx/Spdx-Java-Library/issues/164
      (is (= (licenses-within-text (str "ABCD\n" mpl-2-text              "\nEFGH")) #{"MPL-2.0-no-copyleft-exception" "MPL-2.0"}))
      (is (= (licenses-within-text (str "ABCD\n" clj-spdx-license        "\nEFGH")) #{"Apache-2.0"}))
      (is (= (licenses-within-text (str "ABCD\n" commonmark-java-license "\nEFGH")) #{"BSD-2-Clause"})))
    (testing "Texts with multiple licenses"
      (is (= (licenses-within-text apache-2-gpl-3-text)             #{"Apache-2.0" "GPL-3.0-only" "GPL-3.0+" "GPL-3.0-or-later" "GPL-3.0"}))
;      (is (= (licenses-within-text javamail-license)                #{"CDDL-1.1"   "GPL-2.0"}))    ; Blocked on https://github.com/spdx/Spdx-Java-Library/issues/166
;      (is (= (licenses-within-text jffi-text                        #{"Apache-2.0" "LGPL-3.0-or-later"})))    ; Blocked on https://github.com/jnr/jffi/issues/141
      (is (= (licenses-within-text apache-2-gpl-3-classpath-2-text) #{"Apache-2.0" "GPL-3.0-only" "GPL-3.0+" "GPL-3.0-or-later" "GPL-3.0"})))))

(deftest exceptions-within-text-tests
  (testing "nil, empty string"
    (is (nil? (exceptions-within-text nil)))
    (is (nil? (exceptions-within-text ""))))
  (testing "Text without a valid exception"
    (is (nil? (exceptions-within-text "ABCDEFG"))))
  (testing "Texts with single exceptions and only check for that exception"
    (is (= (exceptions-within-text classpath-2-text #{"Classpath-exception-2.0"}) #{"Classpath-exception-2.0"})))
  (when run-all-slow-tests?
    (testing "Texts with single exceptions and nothing else"
      (is (= (exceptions-within-text classpath-2-text) #{"Classpath-exception-2.0"})))
    (testing "Texts with single exceptions and other text"
      (is (= (exceptions-within-text (str "ABCD\n" classpath-2-text "\nEFGH")) #{"Classpath-exception-2.0"})))
    (testing "Texts with multiple licenses/exceptions"
      (is (= (exceptions-within-text apache-2-gpl-3-classpath-2-text) #{"Classpath-exception-2.0"}))
;      (is (= (exceptions-within-text javamail-license)                #{"Classpath-exception-2.0"}))    ; Blocked on https://github.com/spdx/Spdx-Java-Library/issues/166
    )))
