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

; LicenseRefs are case INsensitive, as of SPDX specification v3.0.2
(def license-ref-fragment-re-d (delay (re/fgrp "i" (re/opt-grp #"DocumentRef-" (re/ncg "DocumentRef" #"[\p{Alnum}\-\.]+") ":")
                                                   "LicenseRef-" (re/ncg "LicenseRef" #"[\p{Alnum}\-\.]+"))))
(def license-ref-re-d          (delay (re/join (re/-lb #"\w") (re/ncg "Identifier" @license-ref-fragment-re-d) (re/-la #"\w"))))

; AdditionRefs are case INsensitive, as of SPDX specification v3.0.2
(def addition-ref-fragment-re-d (delay (re/fgrp "i" (re/opt-grp #"DocumentRef-" (re/ncg "AdditionDocumentRef" #"[\p{Alnum}\-\.]+") ":")
                                                    "AdditionRef-" (re/ncg "AdditionRef" #"[\p{Alnum}\-\.]+"))))
(def addition-ref-re-d          (delay (re/join (re/-lb #"\w") (re/ncg "Identifier" @addition-ref-fragment-re-d) (re/-la #"\w"))))

; Special forms are case INsensitive
(def special-form-fragment-re-d (delay (re/fgrp "i" (re/alt "NONE" "NOASSERTION"))))
(def special-form-re-d          (delay (re/join (re/-lb #"\w") (re/ncg "Identifier" @special-form-fragment-re-d) (re/-la #"\w"))))

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
  @special-form-fragment-re-d
  @special-form-re-d
  nil)
