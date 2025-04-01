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
  "Exception list functionality, primarily provided by `org.spdx.library.model.license.ListedLicenses`."
  (:require [clojure.string    :as s]
            [rencg.api         :as rencg]
            [wreck.api         :as re]
            [spdx.impl.state   :as is]
            [spdx.impl.mapping :as im]
            [spdx.impl.regexes :as ir]
            [spdx.impl.utils   :as u]))

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
  "Is `id` (a `String`) one of the listed SPDX exception ids?"
  [^String id]
  (im/listed-exception-id? id))

(def ^:private id-canonicalisation-d (delay (into {} (map #(vec [(s/lower-case %) %]) (ids)))))

(defn canonicalise-id
  "Canonicalises `id` (an SPDX license exception identifier), by returning it in
  its canonical case.  Returns `nil` if `id` is `nil` or not a listed SPDX license
  exception identifier.

  Notes:

  * This function does _not_ canonicalise a deprecated id to its non-deprecated
    equivalent, since some of those conversions result in an SPDX expression
    rather than an individual id. [[spdx.expressions/parse]] can be used for
    that."
  [^String id]
  (when id
    (get @id-canonicalisation-d (s/lower-case id))))

(defn equivalent-ids?
  "Are `id1` and `id2` (`String`s) equivalent SPDX license exception identifiers
  (i.e. taking the SPDX case sensitivity rules in
  [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/)
  into account)?

  Notes:

  * Returns `false` if `id1` or `id2` are not valid SPDX license exception
    identifiers"
  [^String id1 ^String id2]
  (let [canonical-id1 (canonicalise-id id1)
        canonical-id2 (canonicalise-id id2)]
    (boolean
      (and canonical-id1
           canonical-id2
           (= canonical-id1 canonical-id2)))))

(def ^:private addition-ref-re-d (delay (re/join #"\A" @ir/addition-ref-re-d #"\z")))

(defn addition-ref?
  "Is `s` (a `String`) a valid `AdditionRef`? See
  [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/)
  for specifics."
  [^String s]
  (boolean (when s (re-matches @addition-ref-re-d s))))

(defn addition-ref
  "Constructs an AdditionRef (as a `String`) from individual 'variable
  section' `String`s. Returns `nil` if `addition-ref` is blank, or the resulting
  value is not a valid AdditionRef."
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
    (when-let [m (rencg/re-matches-ncg @addition-ref-re-d s)]
      (merge {:addition-ref (get m "AdditionRef")}
             (when-let [document-ref (get m "AdditionDocumentRef")] {:addition-document-ref document-ref})))))

(defn equivalent-addition-refs?
  "Are `s1` and `s2` (`String`s) equivalent AdditionRefs (i.e. taking the SPDX
  case sensitivity rules in [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity)
  into account)?

  Notes:

  * Returns `false` if `s1` or `s2` are not valid AdditionRefs"
  [^String s1 ^String s2]
  (boolean
    (when-let [addition-ref-1 (string->addition-ref-map s1)]
      (when-let [addition-ref-2 (string->addition-ref-map s2)]
        (and (= (u/safe-lower-case (:addition-document-ref addition-ref-1)) (u/safe-lower-case (:addition-document-ref addition-ref-2)))
             (= (s/lower-case      (:addition-ref          addition-ref-1)) (s/lower-case      (:addition-ref          addition-ref-2))))))))

(defn equivalent?
  "Are `s1` and `s2` (`String`s) equivalent SPDX license exception
  identifiers or AdditionRefs (i.e. taking the SPDX
  case sensitivity rules in [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/)
  into account)?

  Notes:

  * Returns `false` if `s1` or `s2` are not listed SPDX license exception
    identifiers or valid AdditionRefs"
  [^String s1 ^String s2]
  (if (and s1 s2)
    (if (and (listed-id? s1) (listed-id? s2))
      (equivalent-ids? s1 s2)
      (if (and (addition-ref? s1) (addition-ref? s2))
        (equivalent-addition-refs? s1 s2)
        false))
    false))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn id->info
  "Returns SPDX exception list information for `id` (a `String`) as a map, or
  `nil` if `id` is not a valid SPDX exception id.

  `opts` are:

  * `:include-large-text-values?` (default `false`) - controls whether the
    following large text values are included in the result: `:comment :text
    :text-html :text-template`"
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
  "Returns the set of exception ids that identify current (non-deprecated)
  exceptions within the provided set of SPDX license exception ids (or all of
  them, if `ids` not provided)."
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
  (ir/init!)
  ; This is slow mostly due to network I/O (file downloads), so we parallelise to reduce the elapsed time.
  ; Note: using embroidery's pmap* function has been found to be counter-productive here
  (doall (pmap id->info (ids)))
  @id-canonicalisation-d
  @addition-ref-re-d
  nil)
