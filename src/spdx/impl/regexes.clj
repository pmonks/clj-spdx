;
; Copyright © 2024 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.impl.regexes
  "Regex related functionality.  This functionality is bespoke (it does not use
  any logic from `Spdx-Java-Library`)."
  (:require [clojure.string :as s]))

(defn re-escape
  "Escapes `s` (a `String`) for use in a regex. Returns a `String`."
  [s]
  (when s
    (s/escape s {\< "\\<"
                 \( "\\("
                 \[ "\\["
                 \{ "\\{"
                 \\ "\\\\"
                 \^ "\\^"
                 \- "\\-"
                 \= "\\="
                 \$ "\\$"
                 \! "\\!"
                 \| "\\|"
                 \] "\\]"
                 \} "\\}"
                 \) "\\)"
                 \? "\\?"
                 \* "\\*"
                 \+ "\\+"
                 \. "\\."
                 \> "\\>"
                 })))

(defn re-concat
  "Concatenate all of the given `Pattern`s or `String`s into a single `Pattern`."
  [& res]
  (re-pattern (s/join res)))

(defn re-alternation
  "Builds a regex group containing alternations of value of everything in
  `coll` (a sequence of `String`s), returning a `String`.  Each value in `coll`
  will be escaped.

  When non-blank, the (optional) `group-name` parameter turns the group into a
  named capturing group with that name."
  ([coll] (re-alternation nil coll))
  ([group-name coll]
   (str "("
        (when-not (s/blank? group-name) (str "?<" group-name ">"))
        (s/join "|" (map re-escape coll))
        ")")))

(def license-ref-re-d  (delay #"(DocumentRef-(?<DocumentRef>[\p{Alnum}-\.]+):)?LicenseRef-(?<LicenseRef>[\p{Alnum}-\.]+)"))
(def addition-ref-re-d (delay #"(DocumentRef-(?<AdditionDocumentRef>[\p{Alnum}-\.]+):)?AdditionRef-(?<AdditionRef>[\p{Alnum}-\.]+)"))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this method may have a substantial performance cost."
  []
  @license-ref-re-d
  @addition-ref-re-d
  nil)
