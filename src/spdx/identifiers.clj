;
; Copyright © 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.identifiers
  "Identifier related functionality.  This is mostly a convenience namespace
  that delegates to [[spdx.licenses]] or [[spdx.exceptions]] as needed, based on
  the 'type' of an identifier.  This functionality is bespoke (it does not use
  any logic from `Spdx-Java-Library`)."
  (:require [clojure.set     :as set]
            [spdx.licenses   :as lic]
            [spdx.exceptions :as exc]))

(def version  ^{:doc
  "The version of the license list (a `String` in major.minor format)."}
  lic/version)

(defn ids
  "The set of all listed SPDX identifiers."
  []
  (set/union (lic/ids) (exc/ids)))

(defn id-type
  "The 'type' of `id`; one of these values:

  * `:license-id` - listed SPDX license identifier
  * `:exception-id` - listed SPDX exception identifier
  * `:license-ref` - LicenseRef
  * `:addition-ref` - AdditionRef
  * `nil` - `id` is not a listed SPDX identifier, LicenseRef or
    AdditionRef"
  [^String id]
  (when id
    (cond
      (lic/listed-id? id)    :license-id
      (exc/listed-id? id)    :exception-id
      (lic/license-ref? id)  :license-ref
      (exc/addition-ref? id) :addition-ref
      :else                  nil)))

(defn listed-id?
  "Is `id` (a `String`) one of the listed SPDX identifiers?

  Notes:

  * This fn supports any case of id, as per SPDX's case insensitivity rules"
  [^String id]
  (or (lic/listed-id? id)
      (exc/listed-id? id)))

(defn canonicalise-id
  "Canonicalises `id` (an SPDX identifier), by returning it in its canonical
  case.  Returns `nil` if `id` is `nil` or not a listed SPDX identifier.

  Notes:

  * This function does _not_ canonicalise a deprecated identifier to its
    non-deprecated equivalent(s), since some of those conversions result in an
    SPDX expression rather than an individual identifier.
    [[spdx.expressions/canonicalise]] can be used for that."
  [^String id]
  (case (id-type id)
    :license-id   (lic/canonicalise-id id)
    :exception-id (exc/canonicalise-id id)
    nil))

(defn equivalent?
  "Are `s1` and `s2` (`String`s) equivalent SPDX identifiers, LicenseRefs or
  AdditionRefs (i.e. taking the SPDX
  case sensitivity rules in [SPDX Annex B](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/)
  into account)?

  Notes:

  * Returns `false` if `s1` or `s2` are not listed SPDX identifiers or valid
    LicenseRefs or AdditionRefs"
  [^String s1 ^String s2]
  (case [(id-type s1) (id-type s2)]
    [:license-id   :license-id]   (lic/equivalent-ids? s1 s2)
    [:license-ref  :license-ref]  (lic/equivalent-license-refs? s1 s2)
    [:exception-id :exception-id] (exc/equivalent-ids? s1 s2)
    [:addition-ref :addition-ref] (exc/equivalent-addition-refs? s1 s2)
    false))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn id->info
  "Returns SPDX list information for `id` as a map, or `nil` if `id` is not a
  valid SPDX identifier. Includes a `:type` element that identifies whether the
  id is an SPDX license identifier or an SPDX exception identifier, as per
  [[id-type]].

  `opts` are:

  * `:include-large-text-values?` (default `false`) - controls wheter large text
    values are included in the result or not"
  ([^String id] (id->info id nil))
  ([^String id {:keys [include-large-text-values?] :or {include-large-text-values? false} :as opts}]
   (when-let [id-t (id-type id)]
     (case id-t
       :license-id   (assoc (lic/id->info id opts) :type id-t)
       :exception-id (assoc (exc/id->info id opts) :type id-t)
       nil))))

(defn deprecated-id?
  "Is `id` (a `String`) deprecated?  Also returns `false` if `id` is not an SPDX
  listed identifier (including for LicenseRefs and AdditionRefs).

  See [this SPDX FAQ item](https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated)
  for details on what this means."
  [^String id]
  (boolean
    (when-let [info (id->info id)]
      (:deprecated? info))))

(defn non-deprecated-ids
  "Returns the set of SPDX identifiers that identify current (non-deprecated)
  licenses within the provided set of SPDX identifiers (or all of them, if `ids`
  is not provided)."
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
  (lic/init!)
  (exc/init!)
  nil)
