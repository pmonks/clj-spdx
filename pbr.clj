;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

#_{:clj-kondo/ignore [:unresolved-namespace]}
(defn set-opts
  [opts]
  (assoc opts
         :lib          'com.github.pmonks/clj-spdx
         :version      (pbr/calculate-version 1 0)
         :prod-branch  "release"
         :write-pom    true
         :validate-pom true
         :pom          {:description      "Clojure wrapper around spdx/Spdx-Java-Library."
                        :url              "https://github.com/pmonks/clj-spdx"
                        :licenses         [:license   {:name "MPL-2.0" :url "https://www.mozilla.org/en-US/MPL/2.0/"}]
                        :developers       [:developer {:id "pmonks" :name "Peter Monks" :email "pmonks+clj-spdx@gmail.com"}]
                        :scm              {:url                  "https://github.com/pmonks/clj-spdx"
                                           :connection           "scm:git:git://github.com/pmonks/clj-spdx.git"
                                           :developer-connection "scm:git:ssh://git@github.com/pmonks/clj-spdx.git"
                                           :tag                  (tc/git-tag-or-hash)}
                        :issue-management {:system "github" :url "https://github.com/pmonks/clj-spdx/issues"}}
         :codox        {:namespaces ['spdx.exceptions 'spdx.expressions 'spdx.identifiers 'spdx.licenses 'spdx.matching 'spdx.regexes]
                        :metadata   {:doc/format :markdown}}))
