;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.impl.state
  "State management namespace. Note: this namespace is not part of the public
  API of clj-spdx and may change without notice."
  (:require [clojure.string :as s]))

; Static initialisation
(when (s/blank? (System/getProperty "org.spdx.downloadCacheEnabled"))
  (System/setProperty "org.spdx.downloadCacheEnabled" (str true)))  ; Note: unlike Spdx-Java-Library itself, we enable the download cache by default

(def list-obj (delay (org.spdx.library.model.license.ListedLicenses/getListedLicenses)))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning nil. Consumers of this namespace are not required to call
  this fn, as it will be called implicitly upon first use of any of this
  namespace's functionality; it is provided to allow explicit control of the
  cost of initialisation to callers who need it."
  []
  ; Enable download caching in the Spdx-Java-Library (from v1.1.8 onward)
  @list-obj
  nil)
