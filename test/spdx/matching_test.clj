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
            [spdx.test-utils :refer [run-all-tests?]]
            [spdx.matching   :refer [text-is-license? text-is-exception? text-contains-license? text-contains-exception?
                                     texts-equivalent-licenses? texts-equivalent-exceptions? licenses-within-text exceptions-within-text]]))

; Official single license texts
(def apache-10-text                  (delay (slurp "https://www.apache.org/licenses/LICENSE-1.0.txt")))
(def apache-11-text                  (delay (slurp "https://www.apache.org/licenses/LICENSE-1.1.txt")))
(def apache-20-text                  (delay (slurp "https://www.apache.org/licenses/LICENSE-2.0.txt")))

(def epl-10-text                     (delay (slurp "https://www.eclipse.org/org/documents/epl-1.0/EPL-1.0.txt")))
(def epl-20-text                     (delay (slurp "https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.txt")))

(def cddl-10-text                    (delay (slurp "https://spdx.org/licenses/CDDL-1.0.txt")))
(def cddl-11-text                    (delay (slurp "https://spdx.org/licenses/CDDL-1.1.txt")))

(def gpl-10-text                     (delay (slurp "https://www.gnu.org/licenses/gpl-1.0.txt")))
(def gpl-20-text                     (delay (slurp "https://www.gnu.org/licenses/gpl-2.0.txt")))
(def gpl-30-text                     (delay (slurp "https://www.gnu.org/licenses/gpl-3.0.txt")))
(def lgpl-20-text                    (delay (slurp "https://www.gnu.org/licenses/lgpl-2.0.txt")))
(def lgpl-21-text                    (delay (slurp "https://www.gnu.org/licenses/lgpl-2.1.txt")))
(def lgpl-30-text                    (delay (slurp "https://www.gnu.org/licenses/lgpl-3.0.txt")))
(def agpl-30-text                    (delay (slurp "https://www.gnu.org/licenses/agpl-3.0.txt")))

; Note: none of these are readable on JVM 1.8 - it seems to be a CloudFlare encryption problem
(def cc0-10-text                     (delay (slurp "https://creativecommons.org/publicdomain/zero/1.0/legalcode.txt")))
(def cc-by-30-text                   (delay (slurp "https://creativecommons.org/licenses/by/3.0/legalcode.txt")))
(def cc-by-40-text                   (delay (slurp "https://creativecommons.org/licenses/by/4.0/legalcode.txt")))
(def cc-by-sa-40-text                (delay (slurp "https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt")))
(def cc-by-nc-40-text                (delay (slurp "https://creativecommons.org/licenses/by-nc/4.0/legalcode.txt")))
(def cc-by-nc-sa-40-text             (delay (slurp "https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.txt")))
(def cc-by-nd-40-text                (delay (slurp "https://creativecommons.org/licenses/by-nd/4.0/legalcode.txt")))
(def cc-by-nc-nd-40-text             (delay (slurp "https://creativecommons.org/licenses/by-nc-nd/4.0/legalcode.txt")))

(def wtfpl-text                      (delay (slurp "http://www.wtfpl.net/txt/copying/")))

(def mpl-20-text                     (delay (slurp "https://www.mozilla.org/media/MPL/2.0/index.txt")))


; 3rd party software with single licenses
(def clj-spdx-license                (delay (slurp "https://raw.githubusercontent.com/pmonks/clj-spdx/main/LICENSE")))                 ; Apache-2.0
(def commonmark-java-license         (delay (slurp "https://raw.githubusercontent.com/commonmark/commonmark-java/main/LICENSE.txt")))  ; BSD-2-Clause

; Dual license texts
(def apache-20-gpl-30-text           (delay (str "THIS WORK IS DUAL-LICENSED, UNDER:\n\n" @apache-20-text "\n\nOR, AT YOUR DISCRETION:\n\n" @gpl-30-text)))
(def jffi-text                       (delay (slurp "https://raw.githubusercontent.com/jnr/jffi/master/LICENSE")))                      ; Apache-2.0 OR LGPL-3.0+
(def javamail-license                (delay (slurp "https://raw.githubusercontent.com/javaee/javamail/master/LICENSE.txt")))           ; CDDL-1.1 OR GPL-2.0 WITH Classpath-exception-2.0

; Exception texts
(def classpath-20-text               (delay (slurp "./test/data/Classpath-exception-2.0.txt")))

; Dual license plus exception text
(def apache-20-gpl-30-classpath-20-text (delay (str @apache-20-gpl-30-text "\n\n" @classpath-20-text)))

; Note: a lot of these tests are very lightweight, since they would otherwise duplicate unit tests that already exist in the underlying Java library

(deftest text-is-license?-tests
  (testing "nil, empty string"
    (is (false? (text-is-license? nil nil)))
    (is (false? (text-is-license? "" nil)))
    (is (false? (text-is-license? nil "")))
    (is (false? (text-is-license? "" "")))
    (is (false? (text-is-license? @apache-20-text nil)))
    (is (false? (text-is-license? @apache-20-text "")))
    (is (false? (text-is-license? nil "Apache-2.0")))
    (is (false? (text-is-license? "" "Apache-2.0"))))
  (testing "Invalid id"
    (is (false? (text-is-license? @apache-20-text "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Exactly matching official license texts"
    (is (true?  (text-is-license? @apache-20-text            "Apache-2.0")))
    (when run-all-tests?
      (is (true?  (text-is-license? @apache-10-text          "Apache-1.0")))
      (is (true?  (text-is-license? @apache-11-text          "Apache-1.1")))
      (is (true?  (text-is-license? @epl-10-text             "EPL-1.0")))
      (is (true?  (text-is-license? @epl-20-text             "EPL-2.0")))
      (is (true?  (text-is-license? @cddl-10-text            "CDDL-1.0")))
      (is (true?  (text-is-license? @cddl-11-text            "CDDL-1.1")))
      (is (true?  (text-is-license? @gpl-10-text             "GPL-1.0")))
      (is (true?  (text-is-license? @gpl-20-text             "GPL-2.0")))
      (is (true?  (text-is-license? @gpl-30-text             "GPL-3.0")))
      (is (true?  (text-is-license? @lgpl-20-text            "LGPL-2.0")))
      (is (true?  (text-is-license? @lgpl-21-text            "LGPL-2.1")))
      (is (true?  (text-is-license? @lgpl-30-text            "LGPL-3.0")))
      (is (true?  (text-is-license? @agpl-30-text            "AGPL-3.0")))
      (is (true?  (text-is-license? @cc0-10-text             "CC0-1.0")))
      (is (true?  (text-is-license? @cc-by-30-text           "CC-BY-3.0")))
      (is (true?  (text-is-license? @cc-by-40-text           "CC-BY-4.0")))
      (is (true?  (text-is-license? @cc-by-sa-40-text        "CC-BY-SA-4.0")))
      (is (true?  (text-is-license? @cc-by-nc-40-text        "CC-BY-NC-4.0")))
      (is (true?  (text-is-license? @cc-by-nc-sa-40-text     "CC-BY-NC-SA-4.0")))
      (is (true?  (text-is-license? @cc-by-nd-40-text        "CC-BY-ND-4.0")))
      (is (true?  (text-is-license? @cc-by-nc-nd-40-text     "CC-BY-NC-ND-4.0")))
      (is (true?  (text-is-license? @wtfpl-text              "WTFPL")))
      (is (true?  (text-is-license? @mpl-20-text             "MPL-2.0")))))
  (testing "Exactly matching 3rd party license texts"
    (is (true?  (text-is-license? @clj-spdx-license        "Apache-2.0")))
    (when run-all-tests?
      (is (true?  (text-is-license? @commonmark-java-license "BSD-2-Clause"))))))

(deftest text-is-exception?-tests
  (testing "nil, empty string"
    (is (false? (text-is-exception? nil nil)))
    (is (false? (text-is-exception? "" nil)))
    (is (false? (text-is-exception? nil "")))
    (is (false? (text-is-exception? "" "")))
    (is (false? (text-is-exception? @classpath-20-text nil)))
    (is (false? (text-is-exception? @classpath-20-text "")))
    (is (false? (text-is-exception? nil "Classpath-exception-2.0")))
    (is (false? (text-is-exception? "" "Classpath-exception-2.0"))))
  (testing "Invalid id"
    (is (false? (text-is-exception? @classpath-20-text "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Exactly matching official exception texts"
    (is (true?  (text-is-exception? @classpath-20-text "Classpath-exception-2.0")))))

(deftest text-contains-license?-tests
  (testing "nil, empty string"
    (is (false? (text-contains-license? nil nil)))
    (is (false? (text-contains-license? "" nil)))
    (is (false? (text-contains-license? nil "")))
    (is (false? (text-contains-license? "" "")))
    (is (false? (text-contains-license? @apache-20-text nil)))
    (is (false? (text-contains-license? @apache-20-text "")))
    (is (false? (text-contains-license? nil "Apache-2.0")))
    (is (false? (text-contains-license? "" "Apache-2.0"))))
  (testing "Invalid id"
    (is (false? (text-contains-license? @apache-20-text "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Official license text contains license"
    (is (true?  (text-contains-license? @apache-20-text           "Apache-2.0")))
    (when run-all-tests?
    (is (true?  (text-contains-license? @apache-10-text          "Apache-1.0")))
    (is (true?  (text-contains-license? @apache-11-text          "Apache-1.1")))
      (is (true?  (text-contains-license? @epl-10-text             "EPL-1.0")))
      (is (true?  (text-contains-license? @epl-20-text             "EPL-2.0")))
      (is (true?  (text-contains-license? @cddl-10-text            "CDDL-1.0")))
      (is (true?  (text-contains-license? @cddl-11-text            "CDDL-1.1")))
      (is (true?  (text-contains-license? @gpl-10-text             "GPL-1.0")))
      (is (true?  (text-contains-license? @gpl-20-text             "GPL-2.0")))
      (is (true?  (text-contains-license? @gpl-30-text             "GPL-3.0")))
      (is (true?  (text-contains-license? @lgpl-20-text            "LGPL-2.0")))
      (is (true?  (text-contains-license? @lgpl-21-text            "LGPL-2.1")))
      (is (true?  (text-contains-license? @lgpl-30-text            "LGPL-3.0")))
      (is (true?  (text-contains-license? @agpl-30-text            "AGPL-3.0")))
      (is (true?  (text-contains-license? @cc0-10-text             "CC0-1.0")))
      (is (true?  (text-contains-license? @cc-by-30-text           "CC-BY-3.0")))
      (is (true?  (text-contains-license? @cc-by-40-text           "CC-BY-4.0")))
      (is (true?  (text-contains-license? @cc-by-sa-40-text        "CC-BY-SA-4.0")))
      (is (true?  (text-contains-license? @cc-by-nc-40-text        "CC-BY-NC-4.0")))
      (is (true?  (text-contains-license? @cc-by-nc-sa-40-text     "CC-BY-NC-SA-4.0")))
      (is (true?  (text-contains-license? @cc-by-nd-40-text        "CC-BY-ND-4.0")))
      (is (true?  (text-contains-license? @cc-by-nc-nd-40-text     "CC-BY-NC-ND-4.0")))
      (is (true?  (text-contains-license? @wtfpl-text              "WTFPL")))
      (is (true?  (text-contains-license? @mpl-20-text             "MPL-2.0")))))
  (testing "3rd party license text contains license"
    (is (true?  (text-contains-license? @clj-spdx-license        "Apache-2.0")))
    (when run-all-tests?
      (is (true?  (text-contains-license? @commonmark-java-license "BSD-2-Clause")))))
  (testing "Larger texts with junk characters contain licenses"
    (is (true?  (text-contains-license? (str "ABCD\n" @apache-20-text          "\nEFGH") "Apache-2.0")))
    (when run-all-tests?
      (is (true?  (text-contains-license? (str "ABCD\n" @gpl-30-text             "\nEFGH") "GPL-3.0")))
      (is (true?  (text-contains-license? (str "ABCD\n" @cc-by-40-text           "\nEFGH") "CC-BY-4.0")))
      (is (true?  (text-contains-license? (str "ABCD\n" @mpl-20-text             "\nEFGH") "MPL-2.0")))
      (is (true?  (text-contains-license? (str "ABCD\n" @clj-spdx-license        "\nEFGH") "Apache-2.0")))
      (is (true?  (text-contains-license? @jffi-text                                       "Apache-2.0")))
      (is (true?  (text-contains-license? @jffi-text                                       "LGPL-3.0-or-later")))
      (is (true?  (text-contains-license? (str "ABCD\n" @commonmark-java-license "\nEFGH") "BSD-2-Clause"))))))

(deftest text-contains-exception?-tests
  (testing "nil, empty string"
    (is (false? (text-contains-exception? nil nil)))
    (is (false? (text-contains-exception? "" nil)))
    (is (false? (text-contains-exception? nil "")))
    (is (false? (text-contains-exception? "" "")))
    (is (false? (text-contains-exception? @classpath-20-text nil)))
    (is (false? (text-contains-exception? @classpath-20-text "")))
    (is (false? (text-contains-exception? nil "Classpath-exception-2.0")))
    (is (false? (text-contains-exception? "" "Classpath-exception-2.0"))))
  (testing "Invalid id"
    (is (false? (text-contains-exception? @classpath-20-text "INVALID-ID-WHICH-DOES-NOT-EXIST-IN-SPDX-AND-NEVER-WILL"))))
  (testing "Official exception text contains exception"
    (is (true?  (text-contains-exception? @classpath-20-text "Classpath-exception-2.0"))))
  (testing "Larger texts with junk characters contain exceptions"
    (is (true?  (text-contains-exception? (str "ABCD\n" @classpath-20-text "\nEFGH") "Classpath-exception-2.0")))))

(deftest texts-equivalent-licenses?-tests
  (testing "nil, empty string"
    (is (true?  (texts-equivalent-licenses? nil nil)))
    (is (false? (texts-equivalent-licenses? "" nil)))
    (is (false? (texts-equivalent-licenses? nil "")))
    (is (true?  (texts-equivalent-licenses? "" "")))
    (is (false? (texts-equivalent-licenses? @apache-20-text nil)))
    (is (false? (texts-equivalent-licenses? @apache-20-text "")))
    (is (false? (texts-equivalent-licenses? nil @clj-spdx-license)))
    (is (false? (texts-equivalent-licenses? "" @clj-spdx-license))))
  (testing "Equivalent license texts"
    (is (true?  (texts-equivalent-licenses? @apache-20-text @clj-spdx-license)))))

(deftest texts-equivalent-exceptions?-tests
  (testing "nil, empty string"
    (is (true?  (texts-equivalent-exceptions? nil nil)))
    (is (false? (texts-equivalent-exceptions? "" nil)))
    (is (false? (texts-equivalent-exceptions? nil "")))
    (is (true?  (texts-equivalent-exceptions? "" "")))
    (is (false? (texts-equivalent-exceptions? @classpath-20-text nil)))
    (is (false? (texts-equivalent-exceptions? @classpath-20-text "")))
    (is (false? (texts-equivalent-exceptions? nil @classpath-20-text)))
    (is (false? (texts-equivalent-exceptions? "" @classpath-20-text))))
  (testing "Equivalent exception texts"
    (is (true?  (texts-equivalent-exceptions? @classpath-20-text (str "\n\n" @classpath-20-text "\n\n"))))))

(deftest licenses-within-text-tests
  (testing "nil, empty string"
    (is (nil? (licenses-within-text nil)))
    (is (nil? (licenses-within-text ""))))
  (testing "Text without a valid license"
    (is (nil? (licenses-within-text "ABCDEFG"))))
  (testing "Texts with single licenses and only check for one license"
    (is (nil? (licenses-within-text @apache-20-text #{"GPL-3.0"})))
    (is (=    (licenses-within-text @apache-20-text #{"Apache-2.0"}) #{"Apache-2.0"})))
  (testing "Matching official license texts"
    (is (= (licenses-within-text @apache-20-text)      #{"Apache-2.0"}))
    (when run-all-tests?
      (is (= (licenses-within-text @apache-10-text)      #{"Apache-1.0"}))
      (is (= (licenses-within-text @apache-11-text)      #{"Apache-1.1"}))
      (is (= (licenses-within-text @epl-10-text)         #{"EPL-1.0"}))
      (is (= (licenses-within-text @epl-20-text)         #{"EPL-2.0"}))
      (is (= (licenses-within-text @cddl-10-text)        #{"CDDL-1.0"}))
      (is (= (licenses-within-text @cddl-11-text)        #{"CDDL-1.1"}))
      (is (= (licenses-within-text @gpl-10-text)         #{"GPL-1.0-only" "GPL-1.0-or-later" "GPL-1.0+" "GPL-1.0"}))
      (is (= (licenses-within-text @gpl-20-text)         #{"GPL-2.0"}))
      (is (= (licenses-within-text @gpl-30-text)         #{"GPL-3.0-only" "GPL-3.0+" "GPL-3.0-or-later" "GPL-3.0"}))
      (is (= (licenses-within-text @lgpl-20-text)        #{"LGPL-2.0"}))
      (is (= (licenses-within-text @lgpl-21-text)        #{"LGPL-2.1"}))
      (is (= (licenses-within-text @lgpl-30-text)        #{"LGPL-3.0-or-later" "LGPL-3.0+" "LGPL-3.0" "LGPL-3.0-only"}))
      (is (= (licenses-within-text @agpl-30-text)        #{"AGPL-3.0-or-later" "AGPL-3.0-only" "AGPL-3.0"}))
      (is (= (licenses-within-text @cc0-10-text)         #{"CC0-1.0"}))
      (is (= (licenses-within-text @cc-by-30-text)       #{"CC-BY-3.0"}))
      (is (= (licenses-within-text @cc-by-40-text)       #{"CC-BY-4.0"}))
      (is (= (licenses-within-text @cc-by-sa-40-text)    #{"CC-BY-SA-4.0"}))
      (is (= (licenses-within-text @cc-by-nc-40-text)    #{"CC-BY-NC-4.0"}))
      (is (= (licenses-within-text @cc-by-nc-sa-40-text) #{"CC-BY-NC-SA-4.0"}))
      (is (= (licenses-within-text @cc-by-nd-40-text)    #{"CC-BY-ND-4.0"}))
      (is (= (licenses-within-text @cc-by-nc-nd-40-text) #{"CC-BY-NC-ND-4.0"}))
      (is (= (licenses-within-text @wtfpl-text)          #{"WTFPL"}))
      (is (= (licenses-within-text @mpl-20-text)         #{"MPL-2.0-no-copyleft-exception" "MPL-2.0"}))))
  (testing "Matching 3rd party license texts that only contain a single license"
    (is (= (licenses-within-text @clj-spdx-license)        #{"Apache-2.0"}))
    (when run-all-tests?
      (is (= (licenses-within-text @commonmark-java-license) #{"BSD-2-Clause"}))))
  (testing "Matching larger texts with junk characters and a single license"
    (is (= (licenses-within-text (str "ABCD\n" @apache-20-text          "\nEFGH")) #{"Apache-2.0"}))
    (when run-all-tests?
      (is (= (licenses-within-text (str "ABCD\n" @gpl-30-text             "\nEFGH")) #{"GPL-3.0-only" "GPL-3.0+" "GPL-3.0-or-later" "GPL-3.0"}))
      (is (= (licenses-within-text (str "ABCD\n" @cc-by-40-text           "\nEFGH")) #{"CC-BY-4.0"}))
      (is (= (licenses-within-text (str "ABCD\n" @mpl-20-text             "\nEFGH")) #{"MPL-2.0-no-copyleft-exception" "MPL-2.0"}))
      (is (= (licenses-within-text (str "ABCD\n" @wtfpl-text              "\nEFGH")) #{"WTFPL"}))
      (is (= (licenses-within-text (str "ABCD\n" @clj-spdx-license        "\nEFGH")) #{"Apache-2.0"}))
      (is (= (licenses-within-text (str "ABCD\n" @commonmark-java-license "\nEFGH")) #{"BSD-2-Clause"}))))
  (testing "Matching larger texts with multiple licenses and (optionally) other text (e.g. exceptions) that shouldn't match"
    (is (= (licenses-within-text @apache-20-gpl-30-text)              #{"Apache-2.0" "GPL-3.0-only" "GPL-3.0+" "GPL-3.0-or-later" "GPL-3.0"}))
    (when run-all-tests?
      (is (= (licenses-within-text @javamail-license)                   #{"CDDL-1.1"   "GPL-2.0"}))
      (is (= (licenses-within-text @jffi-text)                          #{"Apache-2.0" "LGPL-3.0-or-later" "LGPL-3.0+" "LGPL-3.0" "LGPL-3.0-only"}))
      (is (= (licenses-within-text @apache-20-gpl-30-classpath-20-text) #{"Apache-2.0" "GPL-3.0-only" "GPL-3.0+" "GPL-3.0-or-later" "GPL-3.0"})))))

(deftest exceptions-within-text-tests
  (testing "nil, empty string"
    (is (nil? (exceptions-within-text nil)))
    (is (nil? (exceptions-within-text ""))))
  (testing "Text without a valid exception"
    (is (nil? (exceptions-within-text "ABCDEFG"))))
  (testing "Texts with single exceptions and only check for that exception"
    (is (= (exceptions-within-text @classpath-20-text #{"Classpath-exception-2.0"}) #{"Classpath-exception-2.0"})))
  (testing "Texts with single exceptions and nothing else"
    (is (= (exceptions-within-text @classpath-20-text) #{"Classpath-exception-2.0"})))
  (testing "Texts with single exceptions and other text"
    (is (= (exceptions-within-text (str "ABCD\n" @classpath-20-text "\nEFGH")) #{"Classpath-exception-2.0"})))
  (testing "Texts with multiple licenses/exceptions"
    (is (= (exceptions-within-text @apache-20-gpl-30-classpath-20-text) #{"Classpath-exception-2.0"}))
    (when run-all-tests?
      (is (= (exceptions-within-text @javamail-license)                 #{"Classpath-exception-2.0"})))))
