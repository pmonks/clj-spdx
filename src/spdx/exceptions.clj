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
  "Exception list functionality, primarily provided by `org.spdx.library.model.license.ListedLicenses`."
  (:require [spdx.impl.state   :as is]
            [spdx.impl.mapping :as im]))

(defn version
  "The version of the exception list (a `String` in major.minor format).

  Note: identical to [[spdx.licenses/version]]."
  []
  (.getLicenseListVersion ^org.spdx.library.model.license.ListedLicenses @is/list-obj))

(defn ids
  "The set of all exception ids."
  []
  (some-> (seq (.getSpdxListedExceptionIds ^org.spdx.library.model.license.ListedLicenses @is/list-obj))
          set))

(defn listed-id?
  "Is `id` one of the listed SPDX exception ids?"
  [^String id]
  (im/listed-exception-id? id))

(defn addition-ref?
  "Is `id` an `AdditionRef`?"
  [id]
  (when id (boolean (re-matches #"(DocumentRef-[\p{Alnum}-\.]+:)?AdditionRef-[\p{Alnum}-\.]+" id))))

(defn id->info
  "Returns SPDX exception list information for `id` as a map, or `nil` if `id`
  is not a valid SPDX exception id."
  [^String id]
  (some-> id
          im/id->exception
          im/exception->map))

(defn deprecated-id?
  "Is `id` deprecated?

  See [this SPDX FAQ item](https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated)
  for details on what this means."
  [^String id]
  (when (listed-id? id)
    (boolean (:deprecated? (id->info id)))))

(defn non-deprecated-ids
  "Returns the set of exception ids that identify current (non-deprecated)
  exceptions within the provided set of SPDX exception ids (or all of them, if
  `ids` not provided)."
  ([]    (non-deprecated-ids (ids)))
  ([ids] (some-> (seq (filter (complement deprecated-id?) ids))
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
