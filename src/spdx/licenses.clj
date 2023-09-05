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

(ns spdx.licenses
  "License list functionality, primarily provided by org.spdx.library.model.license.ListedLicenses."
  (:require [spdx.impl.state   :as is]
            [spdx.impl.mapping :as im]))

(defn version
  "The version of the license list (a String in major.minor format).

  Note: identical to spdx.exceptions/version."
  []
  (.getLicenseListVersion ^org.spdx.library.model.license.ListedLicenses @is/list-obj))

(defn ids
  "The set of all license ids."
  []
  (some-> (seq (.getSpdxListedLicenseIds ^org.spdx.library.model.license.ListedLicenses @is/list-obj))
          set))

(defn listed-id?
  "Is the given id one of the listed SPDX license identifiers?"
  [^String id]
  (im/listed-license-id? id))

(defn id->info
  "Returns license information for the given identifier as a map, or nil if
  there isn't one (e.g. the id is nil or invalid)."
  [^String id]
  (some-> id
          im/id->license
          im/license->map))

(defn deprecated-id?
  "Is the given id deprecated?  Returns nil if id is unlisted, or deprecation
  status is undefined in the SPDX license list.

  See https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated
  for more details about what this means."
  [^String id]
  (when (listed-id? id)
    (:deprecated? (id->info id))))

(defn non-deprecated-ids
  "Returns the set of license ids that identify current (non-deprecated)
  licenses within the provided set of SPDX license identifiers (or all of them,
  if not provided)."
  ([]    (non-deprecated-ids (ids)))
  ([ids] (some-> (seq (filter (complement deprecated-id?) ids))
                 set)))

(defn osi-approved-id?
  "Is the given id OSI approved?  Returns nil if id is unlisted, or osi-approval
  is undefined in the SPDX license list.

  See https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md
  for more details about what this means."
  [^String id]
  (when (listed-id? id)
    (:osi-approved? (id->info id))))

(defn osi-approved-ids
  "Returns the set of SPDX license identifiers that identify OSI approved
  licenses within the provided set of SPDX license identifiers (or all of them,
  if not provided)."
  ([]    (osi-approved-ids (ids)))
  ([ids] (some-> (seq (filter osi-approved-id? ids))
                 set)))

(defn fsf-libre-id?
  "Is the given id FSF Libre?  Returns nil if id is unlisted, or FSF fsf-libre
  status is undefined in the SPDX license list.

  See https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md
  for more details about what this means."
  [^String id]
  (when (listed-id? id)
    (:fsf-libre? (id->info id))))

(defn fsf-libre-ids
  "Returns the set of SPDX license identifiers that identify FSF Libre licenses
  within the provided set of SPDX license identifiers (or all of them, if not
  provided). See https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md
  for more details about what this means."
  ([]    (fsf-libre-ids (ids)))
  ([ids] (some-> (seq (filter fsf-libre-id? ids))
                 set)))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning nil. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this method has a substantial performance cost."
  []
  (is/init!)
  ; This is slow mostly due to network I/O (file downloads), so we parallelise to reduce the elapsed time.  It would be
  ; better to use a larger thread pool (pmap is hardcoded to use CPU cores+2 threads), but that would require additional
  ; dependencies (claypoole or dom-top or whatever).
  (doall (pmap id->info (ids)))
  nil)
