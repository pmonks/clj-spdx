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
  "Regex utility namespace. Note: this namespace is not part of the public
  API of clj-spdx and may change without notice."
  (:require [wreck.api :as re]))

; Note: the DocumentRef and LicenseRef portions of a LicenseRef are case-sensitive (see https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity)
(def license-ref-fragment-re-d  (delay #"(?:DocumentRef-(?<DocumentRef>[\p{Alnum}-\.]+):)?LicenseRef-(?<LicenseRef>[\p{Alnum}-\.]+)"))
(def license-ref-re-d           (delay (re/join #"(?<!\w)" @license-ref-fragment-re-d #"(?!\w)")))

; Note: the DocumentRef and AdditionRef portions of an AdditionRef are case-sensitive (see https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity)
(def addition-ref-fragment-re-d (delay #"(?:DocumentRef-(?<AdditionDocumentRef>[\p{Alnum}-\.]+):)?AdditionRef-(?<AdditionRef>[\p{Alnum}-\.]+)"))
(def addition-ref-re-d          (delay (re/join #"(?<!\w)" @addition-ref-fragment-re-d #"(?!\w)")))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this function may have a substantial performance cost."
  []
  @license-ref-fragment-re-d
  @license-ref-re-d
  @addition-ref-fragment-re-d
  @addition-ref-re-d
  nil)
