;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.exceptions
  "Exception list functionality, primarily provided by `org.spdx.library.ListedLicenses`."
  (:require [clojure.string    :as s]
            [rencg.api         :as ncg]
            [embroidery.api    :as e]
            [spdx.impl.state   :as is]
            [spdx.impl.mapping :as im]
            [spdx.impl.regexes :as ir]
            [spdx.impl.utils   :as u]))

(defn version
  "The version of the exception list (a `String` in major.minor(.patchlevel)
  format).

  Note: identical to [[spdx.licenses/version]]."
  []
  (.getLicenseListVersion ^org.spdx.library.ListedLicenses @is/list-obj))

(defn ids
  "The set of all exception ids."
  []
  (some-> (.getSpdxListedExceptionIds ^org.spdx.library.ListedLicenses @is/list-obj)
          seq
          set))

(defn listed-id?
  "Is `id` (a `String`) one of the listed SPDX exception identifiers?

  Notes:

  * This fn supports any case of identifier, as per the SPDX case sensitivity
    rules in [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity)"
  [^String id]
  (im/listed-exception-id? id))

(defn addition-ref?
  "Is `s` (a `String`) a valid AdditionRef? See
  [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/)
  for specifics."
  [^String s]
  (boolean (when s (re-matches @ir/addition-ref-re-d s))))

(defn addition-ref
  "Constructs an AdditionRef (as a `String`) from individual 'variable
  section' `String`s. Returns `nil` if `addition-ref-var-section` is blank, or
  the resulting value is not a valid AdditionRef."
  ([^String addition-ref-var-section] (addition-ref nil addition-ref-var-section))
  ([^String document-ref-var-section ^String addition-ref-var-section]
    (when-not (s/blank? addition-ref-var-section)
      (let [result (str (when document-ref-var-section (str "DocumentRef-" document-ref-var-section ":"))
                        "AdditionRef-" addition-ref-var-section)]
        (when (addition-ref? result)
          result)))))

(defn addition-ref-map->string
  "Turns map `m` representing an AdditionRef into a `String`, returning `nil` if
  `m` is `nil` or the resulting value is not a valid AdditionRef.

  Note:

  * This fn is the inverse of [[string->addition-ref-map]]."
  [^java.util.Map m]
  (when m
    (addition-ref (:addition-document-ref m) (:addition-ref m))))

(defn string->addition-ref-map
  "Turns `s` (a `String` containing an AdditionRef) into a `map` representing
  that same AdditionRef.  Returns `nil` if `s` is `nil` or not a valid
  AdditionRef.

  Note:

  * This fn is the inverse of [[addition-ref-map->string]]."
  [^String s]
  (when s
    (when-let [m (ncg/re-matches @ir/addition-ref-re-d s)]
      (merge {:addition-ref (get m "AdditionRef")}
             (when-let [document-ref (get m "AdditionDocumentRef")] {:addition-document-ref document-ref})))))

(def ^:private id-canonicalisation-d (delay (into {} (map #(vec [(s/lower-case %) %]) (ids)))))

(defn canonicalise
  "Canonicalises `s` (an SPDX exception identifier or AdditionRef), by returning
  it in its canonical case.  Returns `nil` if `s` is `nil` or not a listed SPDX
  exception identifier or AdditionRef.

  Notes:

  * This function does _not_ canonicalise a deprecated id to its non-deprecated
    equivalent, since some of those conversions result in an SPDX expression
    rather than an individual id. [[spdx.expressions/parse]] can be used for
    that."
  [^String s]
  (when s
    (if-let [id (get @id-canonicalisation-d (s/lower-case s))]
      id
      (when-let [addition-ref-map (string->addition-ref-map s)]
        (addition-ref-map->string addition-ref-map)))))

(defn ^:deprecated ^:no-doc canonicalise-id
  "Superceded by [[canonicalise]]."
  [id]
  (canonicalise id))

(defn equivalent?
  "Are `s1` and `s2` (`String`s) equivalent SPDX exception identifiers or
  AdditionRefs (i.e. taking the SPDX case sensitivity rules in [SPDX
  Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity)
  into account)?"
  [^String s1 ^String s2]
  (boolean
    (or (and (nil? s1) (nil? s2))
        (let [cs1 (canonicalise s1)
              cs2 (canonicalise s2)]
          (and cs1 cs2 (= (s/lower-case cs1) (s/lower-case cs2)))))))

(defn ^:deprecated ^:no-doc equivalent-ids?
  "Superceded by [[equivalent?]]"
  [^String id1 ^String id2]
  (equivalent? id1 id2))

(defn ^:deprecated ^:no-doc equivalent-addition-refs?
  "Superceded by [[equivalent?]]"
  [^String ar1 ^String ar2]
  (equivalent? ar1 ar2))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn id->info
  "Returns SPDX exception list information for `id` (a `String`) as a map, or
  `nil` if `id` is not a valid SPDX exception identifier.

  `opts` are:

  * `:include-large-text-values?` (default `false`) - controls whether large
    text values are included in the result or not"
  ([^String id] (id->info id nil))
  ([^String id {:keys [include-large-text-values?] :or {include-large-text-values? false} :as opts}]
   (some-> id
           im/id->exception
           (im/exception->map opts))))

(defn deprecated-id?
  "Is `id` (a `String`) deprecated?  Also returns `false` if `id` is not in the
  SPDX license exception list.

  See [this SPDX FAQ item](https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated)
  for details on what this means."
  [^String id]
  (boolean (when (listed-id? id) (:deprecated? (id->info id)))))

(defn non-deprecated-ids
  "Returns the set of SPDX exception identifiers that identify current
  (non-deprecated) exceptions within the provided set of SPDX exception
  identifiers (or all of them, if `ids` not provided)."
  ([]    (non-deprecated-ids (ids)))
  ([ids] (some-> (filter (complement deprecated-id?) ids)
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
