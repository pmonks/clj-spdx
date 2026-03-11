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
  "License list functionality, primarily provided by `org.spdx.library.ListedLicenses`.

  Notes:

  * The functions in this namespace support any case of identifier or
    LicenseRef, as per the SPDX case sensitivity rules in [SPDX Specification Annex B](https://spdx.github.io/spdx-spec/v3.0.2/annexes/spdx-license-expressions/#case-sensitivity)"
  (:require [clojure.string    :as s]
            [rencg.api         :as ncg]
            [embroidery.api    :as e]
            [spdx.impl.state   :as is]
            [spdx.impl.mapping :as im]
            [spdx.impl.regexes :as ir]
            [spdx.impl.utils   :as u]))

(defn version
  "The version of the license list (a `String` in major.minor(.patchlevel)
  format).

  Note: identical to [[spdx.identifiers/version]]."
  []
  (.getLicenseListVersion ^org.spdx.library.ListedLicenses @is/list-obj))

(defn ids
  "The set of all SPDX license identifiers."
  []
  (some-> (.getSpdxListedLicenseIds ^org.spdx.library.ListedLicenses @is/list-obj)
          seq
          set))

(defn listed-id?
  "Is `s` (a `String`) one of the listed SPDX license identifiers?"
  [^String s]
  (im/listed-license-id? s))

(defn license-ref?
  "Is `s` (a `String`) a valid LicenseRef? See
  [SPDX Specification Annex B](https://spdx.github.io/spdx-spec/v3.0.2/annexes/spdx-license-expressions/)
  for specifics."
  [s]
  (boolean (when s (re-matches @ir/license-ref-re-d s))))

(defn license-ref
  "Constructs a LicenseRef (as a `String`) from individual 'variable
  section' `String`s. Returns `nil` if `license-ref-var-section` is blank, or
  the resulting value is not a valid LicenseRef."
  ([^String license-ref-var-section] (license-ref nil license-ref-var-section))
  ([^String document-ref-var-section ^String license-ref-var-section]
   (when-not (s/blank? license-ref-var-section)
     (let [result (str (when document-ref-var-section (str "DocumentRef-" document-ref-var-section ":"))
                       "LicenseRef-" license-ref-var-section)]
       (when (license-ref? result)
         result)))))

(defn license-ref-map->string
  "Turns map `m` representing a LicenseRef into a `String`, returning `nil` if
  `m` is `nil` or the resulting value is not a valid LicenseRef.

  Notes:

  * This fn is the inverse of [[string->license-ref-map]]."
  [^java.util.Map m]
  (when (and m (contains? m :license-ref))
    (license-ref (:document-ref m) (:license-ref m))))

(defn string->license-ref-map
  "Turns `s` (a `String` containing a LicenseRef) into a `map` representing that
  same LicenseRef.  Returns `nil` if `s` is `nil` or not a valid LicenseRef.

  Notes:

  * This fn is the inverse of [[license-ref-map->string]].
  * This is equivalent to calling [[spdx.expressions/parse]] with `s`."
  [^String s]
  (when s
    (when-let [m (ncg/re-matches @ir/license-ref-re-d s)]
      (merge {:license-ref (get m "LicenseRef")}
             (when-let [document-ref (get m "DocumentRef")] {:document-ref document-ref})))))

(def ^:private id-canonicalisation-d (delay (into {} (map #(vec [(s/lower-case %) %]) (ids)))))

(defn canonicalise
  "Canonicalises `s` (an SPDX license identifier or LicenseRef), by returning it
  in its canonical case.  Returns `nil` if `s` is `nil` or not a listed SPDX
  license identifier or LicenseRef.

  Notes:

  * This function does _not_ canonicalise a deprecated identifier to its
    non-deprecated equivalent(s), since some of those conversions result in an
    SPDX expression rather than an individual identifier.
    [[spdx.expressions/canonicalise]] can be used for that."
  [^String s]
  (when s
    (if-let [id (get @id-canonicalisation-d (s/lower-case s))]
      id
      (when-let [license-ref-map (string->license-ref-map s)]
        (license-ref-map->string license-ref-map)))))

(defn ^:deprecated ^:no-doc canonicalise-id
  "Superceded by [[canonicalise]]."
  [^String s]
  (canonicalise s))

(defn equivalent?
  "Are `s1` and `s2` (`String`s) equivalent SPDX license identifiers or
  LicenseRefs (i.e. taking the SPDX case sensitivity rules in [SPDX
  Annex B](https://spdx.github.io/spdx-spec/v3.0.2/annexes/spdx-license-expressions/#case-sensitivity)
  into account)?"
  [^String s1 ^String s2]
  (boolean
    (or (and (nil? s1) (nil? s2))
        (let [cs1 (canonicalise s1)
              cs2 (canonicalise s2)]
          (and cs1 cs2 (= (s/lower-case cs1) (s/lower-case cs2)))))))

(defn ^:deprecated ^:no-doc equivalent-ids?
  "Superceded by [[equivalent?]]"
  [^String s1 ^String s2]
  (equivalent? s1 s2))

(defn ^:deprecated ^:no-doc equivalent-license-refs?
  "Superceded by [[equivalent?]]"
  [^String s1 ^String s2]
  (equivalent? s1 s2))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn id->info
  "Returns SPDX license list information for `id` as a map, or `nil` if `id` is
  not a listed SPDX license identifier.

  `opts` are:

  * `:include-large-text-values?` (default `false`) - controls whether large
    text values are included in the result or not"
  ([^String id] (id->info id nil))
  ([^String id {:keys [include-large-text-values?] :or {include-large-text-values? false} :as opts}]
   (some-> id
           canonicalise
           im/id->license
           (im/license->map opts))))

(defn deprecated-id?
  "Is `id` (a `String`) deprecated?  Also returns `false` if `id` is not a
  listed SPDX license identifier.

  See [this SPDX FAQ item](https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated)
  for details on what this means."
  [^String id]
  (boolean (when (listed-id? id) (:deprecated? (id->info id)))))

(defn non-deprecated-ids
  "Returns the set of SPDX license identifiers that identify current
  (non-deprecated) licenses, within the provided set of listed SPDX license
  identifiers or all listed identifiers when `ids` not provided."
  ([]    (non-deprecated-ids (ids)))
  ([ids] (some-> (filter (complement deprecated-id?) ids)
                 seq
                 set)))

(defn osi-approved-id?
  "Is `id` (a `String`) OSI Approved?  Also returns `false` if `id` is not a
  listed SPDX license identifier.

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'OSI Approved' means."
  [^String id]
  (boolean (when (listed-id? id) (:osi-approved? (id->info id)))))

(defn osi-approved-ids
  "Returns the set of SPDX license identifiers that identify OSI Approved
  licenses within the provided set of SPDX license identifiers (or all of them,
  if `ids` is not provided).

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'OSI Approved' means."
  ([]    (osi-approved-ids (ids)))
  ([ids] (some-> (filter osi-approved-id? ids)
                 seq
                 set)))

(defn fsf-libre-id?
  "Is `id` (a `String`) FSF Libre?  Also returns `false` if `id` is not in the
  SPDX license list.

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'FSF Libre' means."
  [^String id]
  (boolean (when (listed-id? id) (:fsf-libre? (id->info id)))))

(defn fsf-libre-ids
  "Returns the set of SPDX license identifiers that identify FSF Libre licenses
  within the provided set of SPDX license identifiers (or all of them, if `ids`
  is not provided).

  See [this reference](https://github.com/spdx/license-list-XML/blob/main/DOCS/license-fields.md)
  for details about what 'FSF Libre' means."
  ([]    (fsf-libre-ids (ids)))
  ([ids] (some-> (filter fsf-libre-id? ids)
                 seq
                 set)))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this function may have a substantial performance cost."
  []
  (is/init!)
  (ir/init!)
  ; This is slow mostly due to network I/O (file downloads), so we parallelise to reduce the elapsed time.
  (doall (e/bounded-pmap* u/maximum-concurrency id->info (ids)))
  @id-canonicalisation-d
  nil)
