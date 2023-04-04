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

(ns spdx.exceptions
  "Exception list functionality, primarily provided by org.spdx.library.model.license.ListedLicenses."
  (:require [spdx.impl.state   :as is]
            [spdx.impl.mapping :as im]))

(defn version
  "The version of the exception list (a String in major.minor format). Note: identical to spdx.license-list/version."
  []
  (.getLicenseListVersion ^org.spdx.library.model.license.ListedLicenses @is/list-obj))

(defn ids
  "The set of all exception ids."
  []
  (some-> (seq (.getSpdxListedExceptionIds ^org.spdx.library.model.license.ListedLicenses @is/list-obj))
          set))

(defn listed-id?
  "Is the given id one of the listed SPDX exception identifiers?"
  [^String id]
  (im/listed-exception-id? id))

(defn id->info
  "Returns exception information for the given identifier as a map, or nil if there isn't one (e.g. the id is nil)."
  [^String id]
  (some-> id
          im/id->exception
          im/exception->map))

(defn non-deprecated-ids
  "Returns the set of exception ids that identify current (non-deprecated) exceptions within the provided set of SPDX exception identifiers (or all of them, if not provided)."
  ([]    (non-deprecated-ids (ids)))
  ([ids] (some-> (seq (filter #(not (:deprecated? (id->info %))) ids))
                 set)))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent calls), returning nil. Consumers of this namespace are not required to call this fn, as it will be called implicitly upon first use of any of this namespace's functionality; it is provided to allow explicit control of the cost of initialisation to callers who need it."
  []
  (run! id->info (ids)))
