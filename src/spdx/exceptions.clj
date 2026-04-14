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
  "Exception list functionality, primarily provided by `org.spdx.library.ListedLicenses`.

  Notes:

  * The functions in this namespace support any case of identifier or
    AdditionRef, as per the SPDX case sensitivity rules in [SPDX Specification Annex B](https://spdx.github.io/spdx-spec/v3.0.2/annexes/spdx-license-expressions/#case-sensitivity)"
  (:require [clojure.string    :as s]
            [rencg.api         :as ncg]
            [embroidery.api    :as e]
            [spdx.impl.state   :as sis]
            [spdx.impl.mapping :as sim]
            [spdx.impl.regexes :as sir]
            [spdx.impl.utils   :as siu]))

(defn version
  "The version of the exception list (a `String` in \"major.minor(.patchlevel)\"
  format).

  Note: identical to [[spdx.identifiers/version]]."
  []
  (.getLicenseListVersion ^org.spdx.library.ListedLicenses @sis/list-obj))

(defn ids
  "The set of all exception ids."
  []
  (some-> (.getSpdxListedExceptionIds ^org.spdx.library.ListedLicenses @sis/list-obj)
          seq
          set))

(defn listed?
  "Is `s` (a `String`) one of the listed SPDX exception identifiers?"
  [^String s]
  (sim/listed-exception-id? s))

(defn addition-ref?
  "Is `s` (a `String`) a valid AdditionRef? See
  [SPDX Specification Annex B](https://spdx.github.io/spdx-spec/v3.0.2/annexes/spdx-license-expressions/)
  for specifics."
  [^String s]
  (boolean (when s (re-matches @sir/addition-ref-re-d s))))

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
  `m` is `nil` or the resulting value is not a valid AdditionRef.  Keys in the
  map are as for [[string->addition-ref-map]].

  Note:

  * This fn is the inverse of [[string->addition-ref-map]]."
  [^java.util.Map m]
  (when m
    (addition-ref (:addition-document-ref m) (:addition-ref m))))

(defn string->addition-ref-map
  "Turns `s` (a `String` containing an AdditionRef) into a `map` representing
  that same AdditionRef.  Returns `nil` if `s` is `nil` or not a valid
  AdditionRef.

  Keys in the map:

  * `:addition-ref` (`String`, mandatory) - the value of the variable tag in the
    AdditionRef component
  * `:addition-document-ref` (`String`, optional) - the value of the variable
    tag in the DocumentRef component

  Note:

  * This fn is the inverse of [[addition-ref-map->string]]."
  [^String s]
  (when s
    (when-let [m (ncg/re-matches @sir/addition-ref-re-d s)]
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

(defn equivalent?
  "Are `s1` and `s2` (`String`s) equivalent SPDX exception identifiers or
  AdditionRefs (i.e. taking the SPDX case sensitivity rules in [SPDX
  Annex B](https://spdx.github.io/spdx-spec/v3.0.2/annexes/spdx-license-expressions/#case-sensitivity)
  into account)?

  Notes:

  * Returns `true` if `s1` and `s2` are both `nil`.
  * Returns `false` if `s1` or `s2` are not listed SPDX exception identifiers or
    AdditionRefs, even if they are otherwise equal."
  [^String s1 ^String s2]
  (boolean
    (or (and (nil? s1) (nil? s2))
        (let [cs1 (canonicalise s1)
              cs2 (canonicalise s2)]
          (and cs1 cs2 (= (s/lower-case cs1) (s/lower-case cs2)))))))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn info
  "Returns SPDX exception list information for `id` (a `String`) as a map, or
  `nil` if `id` is not a listed SPDX exception identifier.

  `opts` are:

  * `:include-large-text-values?` (default `false`) - controls whether large
    text values are included in the result or not"
  ([^String id] (info id nil))
  ([^String id {:keys [include-large-text-values?] :or {include-large-text-values? false} :as opts}]
   (some-> id
           canonicalise
           sim/id->exception
           (sim/exception->map opts))))

(defn deprecated?
  "Is `id` (a `String`) deprecated?  Also returns `false` if `id` is not a
  listed SPDX exception identifier.

  See [this SPDX FAQ item](https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated)
  for details on what this means."
  [^String id]
  (boolean (when (listed? id) (:deprecated? (info id)))))

(defn non-deprecated-ids
  "Returns the set of SPDX exception identifiers that identify current
  (non-deprecated) exceptions, within the provided set of listed SPDX
  exception identifiers or all listed identifiers when `ids` not
  provided."
  ([]    (non-deprecated-ids (ids)))
  ([ids] (some-> (filter (complement deprecated?) ids)
                 seq
                 set)))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: [this function may have a substantial performance cost](https://github.com/pmonks/clj-spdx?tab=readme-ov-file#a-note-about-spdx-license-list-assets)."
  []
  (sis/init!)
  (sir/init!)
  ; This is slow mostly due to network I/O (file downloads), so we parallelise to reduce the elapsed time.
  (doall (e/bounded-pmap* siu/maximum-concurrency info (ids)))
  @id-canonicalisation-d
  nil)


; Deprecated vars, to be removed in the next major version
(defn ^:deprecated ^:no-doc listed-id?
  "Superceded by [[listed?]]."
  [^String s]
  (listed? s))

(defn ^:deprecated ^:no-doc canonicalise-id
  "Superceded by [[canonicalise]]."
  [^String s]
  (canonicalise s))

(defn ^:deprecated ^:no-doc equivalent-ids?
  "Superceded by [[equivalent?]]"
  [^String s1 ^String s2]
  (equivalent? s1 s2))

(defn ^:deprecated ^:no-doc equivalent-addition-refs?
  "Superceded by [[equivalent?]]"
  [^String s1 ^String s2]
  (equivalent? s1 s2))

(defn ^:deprecated ^:no-doc id->info
  "Superceded by [[info]]"
  ([^String id]      (info id))
  ([^String id opts] (info id opts)))

(defn ^:deprecated ^:no-doc deprecated-id?
  "Superceded by [[deprecated?]]"
  [^String id]
  (deprecated? id))
