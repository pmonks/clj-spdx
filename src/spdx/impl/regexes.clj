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
  API of clj-spdx and may change without notice.")

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
