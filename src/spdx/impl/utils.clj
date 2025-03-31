;
; Copyright © 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.impl.utils
  "Utility namespace. Note: this namespace is not part of the public API of
  clj-spdx and may change without notice."
  (:require [clojure.string :as s]))

(defn safe-lower-case
  "Because clojure.string is not `nil` tolerant. 🙄"
  [s]
  (when s
    (s/lower-case s)))
