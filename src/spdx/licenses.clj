;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.licenses
  "License list functionality, primarily provided by `org.spdx.library.model.license.ListedLicenses`."
  (:require [clojure.string    :as s]
            [rencg.api         :as rencg]
            [wreck.api         :as re]
            [spdx.impl.state   :as is]
            [spdx.impl.mapping :as im]
            [spdx.impl.regexes :as ir]
            [spdx.impl.utils   :as u]))

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

(def ^:private license-ref-re-d (delay (re/join #"\A" @ir/license-ref-re-d #"\z")))

(defn license-ref?
  "Is `id` a `LicenseRef`?"
  [id]
  (boolean (when id (re-matches @license-ref-re-d id))))

(defn license-ref
  "Constructs a LicenseRef (as a `String`) from individual 'variable
  section' `String`s. Returns `nil` if `license-ref` is blank, or the resulting
  value is not a valid LicenseRef (see
  [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/))."
  ([license-ref-var-section] (license-ref nil license-ref-var-section))
  ([document-ref-var-section license-ref-var-section]
    (when-not (s/blank? license-ref-var-section)
      (let [result (str (when document-ref-var-section (str "DocumentRef-" document-ref-var-section ":"))
                        "LicenseRef-" license-ref-var-section)]
        (when (license-ref? result)
          result)))))

(defn license-ref-map->string
  "Turns map `m` representing a LicenseRef into a `String`, returning `nil` if
  `m` is `nil` or the resulting value is not a valid LicenseRef (see
  [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/))."
  [m]
  (when m
    (license-ref (:document-ref m) (:license-ref m))))

(defn string->license-ref-map
  "Turns `s` (a `String`) into a `map` representing a LicenseRef.  Returns `nil`
  if `s` is `nil` or not a valid LicenseRef (see
  [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/)).

  Notes:

  * This is equivalent to calling [[spdx.expressions/parse]] with `s`."
  [s]
  (when s
    (when-let [m (rencg/re-matches-ncg @license-ref-re-d s)]
      (merge {:license-ref (get m "LicenseRef")}
             (when-let [document-ref (get m "DocumentRef")] {:document-ref document-ref})))))

(defn equivalent-license-refs?
  "Are `s1` and `s2` (`String`s) equivalent LicenseRefs (i.e. taking the SPDX
  case sensitivity rules in [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/)
  into account)?

  Notes:

  * Returns `false` if either `s1` or `s2` are not valid LicenseRefs"
  [s1 s2]
  (boolean
    (when-let [license-ref-1 (string->license-ref-map s1)]
      (when-let [license-ref-2 (string->license-ref-map s2)]
        (and (= (u/safe-lower-case (:document-ref license-ref-1)) (u/safe-lower-case (:document-ref license-ref-2)))
             (= (s/lower-case      (:license-ref  license-ref-1)) (s/lower-case      (:license-ref  license-ref-2))))))))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn id->info
  "Returns SPDX license list information for `id` as a map, or `nil` if `id` is
  not a valid SPDX license id.

  `opts` are:

  * `:include-large-text-values?` (default `false`) - controls whether the
    following large text values are included in the result: `:comment :text
    :text-html :text-template :header :header-html :header-template`"
  ([^String id] (id->info id nil))
  ([^String id {:keys [include-large-text-values?] :or {include-large-text-values? false} :as opts}]
   (some-> id
           im/id->license
           (im/license->map opts))))

(defn deprecated-id?
  "Is `id` deprecated?  Also returns `false` if `id` is not in the SPDX license list.

  See [this SPDX FAQ item](https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated)
  for details on what this means."
  [^String id]
  (boolean (when (listed-id? id) (:deprecated? (id->info id)))))

(defn non-deprecated-ids
  "Returns the set of license ids that identify current (non-deprecated)
  licenses within the provided set of SPDX license ids (or all of them, if `ids`
  is not provided)."
  ([]    (non-deprecated-ids (ids)))
  ([ids] (some-> (seq (filter (complement deprecated-id?) ids))
                 set)))

(defn osi-approved-id?
  "Is `id` OSI Approved?  Also returns `false` if `id` is not in the SPDX license list.

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'OSI Approved' means."
  [^String id]
  (boolean (when (listed-id? id) (:osi-approved? (id->info id)))))

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
  "Is `id` FSF Libre?  Also returns `false` if `id` is not in the SPDX license list.

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'FSF Libre' means."
  [^String id]
  (boolean (when (listed-id? id) (:fsf-libre? (id->info id)))))

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
  (ir/init!)
  ; This is slow mostly due to network I/O (file downloads), so we parallelise to reduce the elapsed time.
  ; Note: using embroidery's pmap* function has been found to be counter-productive here
  (doall (pmap id->info (ids)))
  @license-ref-re-d
  nil)
