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
  "License list functionality, primarily provided by `org.spdx.library.model.license.ListedLicenses`."
  (:require [spdx.impl.state   :as is]
            [spdx.impl.mapping :as im]))

(defn version
  "The version of the license list (a `String` in major.minor format).

  Note: identical to [[spdx.exceptions/version]]."
  []
  (.getLicenseListVersion ^org.spdx.library.model.license.ListedLicenses @is/list-obj))

(defn ids
  "The set of all license ids."
  []
  (some-> (seq (.getSpdxListedLicenseIds ^org.spdx.library.model.license.ListedLicenses @is/list-obj))
          set))

(defn listed-id?
  "Is `id` one of the listed SPDX license identifiers?"
  [^String id]
  (im/listed-license-id? id))

(defn license-ref?
  "Is `id` a `LicenseRef`?"
  [id]
  (when id (boolean (re-matches #"(DocumentRef-[\p{Alnum}-\.]+:)?LicenseRef-[\p{Alnum}-\.]+" id))))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn id->info
  "Returns SPDX license list information for `id` as a map, or `nil` if `id` is
  not a valid SPDX license id.

  `opts` are:

  * `:include-large-text-values?` (default `true`) - controls whether the
    following large text values are included in the result: `:comment :text
    :text-html :text-template :header :header-html :header-template`"
  ([^String id] (id->info id nil))
  ([^String id {:keys [include-large-text-values?] :or {include-large-text-values? true} :as opts}]
   (some-> id
           im/id->license
           (im/license->map opts))))

(defn deprecated-id?
  "Is `id` deprecated?

  See [this SPDX FAQ item](https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated)
  for details on what this means."
  [^String id]
  (when (listed-id? id)
    (:deprecated? (id->info id))))

(defn non-deprecated-ids
  "Returns the set of license ids that identify current (non-deprecated)
  licenses within the provided set of SPDX license ids (or all of them, if `ids`
  is not provided)."
  ([]    (non-deprecated-ids (ids)))
  ([ids] (some-> (seq (filter (complement deprecated-id?) ids))
                 set)))

(defn osi-approved-id?
  "Is `id` OSI Approved?  Returns `nil` if `id` is unlisted, or OSI Approval is
  undefined in the SPDX license list for this license id.

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'OSI Approved' means."
  [^String id]
  (when (listed-id? id)
    (:osi-approved? (id->info id))))

(defn osi-approved-ids
  "Returns the set of SPDX license ids that identify OSI Approved licenses
  within the provided set of SPDX license ids (or all of them, if `ids` is not
  provided).

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'OSI Approved' means."
  ([]    (osi-approved-ids (ids)))
  ([ids] (some-> (seq (filter osi-approved-id? ids))
                 set)))

(defn fsf-libre-id?
  "Is `id` FSF Libre?  Returns `nil` if `id` is unlisted, or FSF Libre status is
  undefined in the SPDX license list.

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'FSF Libre' means."
  [^String id]
  (when (listed-id? id)
    (:fsf-libre? (id->info id))))

(defn fsf-libre-ids
  "Returns the set of SPDX license ids that identify FSF Libre licenses within
  the provided set of SPDX license ids (or all of them, if `ids` is not
  provided).

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'FSF Libre' means."
  ([]    (fsf-libre-ids (ids)))
  ([ids] (some-> (seq (filter fsf-libre-id? ids))
                 set)))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this method may have a substantial performance cost."
  []
  (is/init!)
  ; This is slow mostly due to network I/O (file downloads), so we parallelise to reduce the elapsed time.
  ; Note: using embroidery's pmap* function has been found to be counter-productive here
  (doall (pmap id->info (ids)))
  nil)
