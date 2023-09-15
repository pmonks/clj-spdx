;
; Copyright © 2023 Peter Monks
;
; Licensed under the Apache License Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;
; SPDX-License-Identifier: Apache-2.0
;

(ns spdx.impl.state
  "State management namespace. Note: this namespace is not part of the public
  API of clj-spdx and may change without notice."
  (:require [clojure.string :as s]))

(def list-obj (delay (org.spdx.library.model.license.ListedLicenses/getListedLicenses)))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning nil. Consumers of this namespace are not required to call
  this fn, as it will be called implicitly upon first use of any of this
  namespace's functionality; it is provided to allow explicit control of the
  cost of initialisation to callers who need it."
  []
  ; Enable download caching in the Spdx-Java-Library (from v1.1.8 onward)
  (when (s/blank? (System/getProperty "org.spdx.storage.listedlicense.enableCache"))
    (System/setProperty "org.spdx.storage.listedlicense.enableCache" (str true)))  ; Note: unlike Spdx-Java-Library itself, we enable the download cache by default
  @list-obj
  nil)
