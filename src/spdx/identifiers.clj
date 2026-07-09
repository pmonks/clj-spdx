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
  the 'type' of an identifier.  This functionality is bespoke (it does not
  directly use any logic from `Spdx-Java-Library`).

  Notes:

  * The functions in this namespace support any case of identifier, LicenseRef,
    or AdditionRef, as per the SPDX case sensitivity rules in [SPDX Specification Annex B](https://spdx.github.io/spdx-spec/v3.0.2/annexes/spdx-license-expressions/#case-sensitivity)"
  (:require [clojure.set     :as set]
            [spdx.licenses   :as sl]
            [spdx.exceptions :as se]))

(def ^{:arglists '([])} version
  "The version of the license list (a `String` in \"major.minor(.patchlevel)\"
  format)."
  sl/version)

(defn ids
  "The set of all listed SPDX identifiers."
  []
  (set/union (sl/ids) (se/ids)))

(defn id-type
  "The 'type' of `s`; one of these values:

  * `:license-id` - listed SPDX license identifier
  * `:exception-id` - listed SPDX exception identifier
  * `:license-ref` - LicenseRef
  * `:addition-ref` - AdditionRef
  * `:special-form` - one of the special forms (`NONE`, `NOASSERTION`)
  * `nil` - `s` is not a listed SPDX identifier, LicenseRef or
    AdditionRef"
  [^String s]
  (when s
    (cond
      (sl/listed?       s) :license-id
      (se/listed?       s) :exception-id
      (sl/license-ref?  s) :license-ref
      (se/addition-ref? s) :addition-ref
      (sl/special-form? s) :special-form
      :else                nil)))

(defn listed?
  "Is `s` (a `String`) one of the listed SPDX identifiers?"
  [^String s]
  (boolean
    (or (sl/listed? s)
        (se/listed? s))))

(defn canonicalise
  "Canonicalises `s` (an SPDX identifier, Ref, or special form), by returning it
  in its canonical case.  Returns `nil` if `s` is `nil` or not a listed SPDX
  identifier, LicenseRef, AdditionRef or special form (`NONE`, `NOASSERTION`).

  Notes:

  * This function does _not_ canonicalise a deprecated identifier to its
    non-deprecated equivalent(s), since some of those conversions result in an
    SPDX expression rather than an individual identifier.
    [[spdx.expressions/canonicalise]] can be used for that."
  [^String s]
  (case (id-type s)
    (:license-id  :license-ref :special-form) (sl/canonicalise s)
    (:exception-id :addition-ref)              (se/canonicalise s)
    nil))

(defn equivalent?
  "Are `s1` and `s2` (`String`s) equivalent SPDX identifiers, LicenseRefs or
  AdditionRefs (i.e. taking the SPDX case sensitivity rules in
  [SPDX Specification Annex B](https://spdx.github.io/spdx-spec/v3.0.2/annexes/spdx-license-expressions/#case-sensitivity)
  into account)?

  Notes:

  * Returns `true` if `s1` and `s2` are both `nil`.
  * Returns `false` if `s1` or `s2` are not listed SPDX identifiers or
    LicenseRefs or AdditionRefs, even if they are otherwise equal."
  [^String s1 ^String s2]
  (boolean
    (or (and (nil? s1) (nil? s2))
        (case [(id-type s1) (id-type s2)]
          ([:license-id   :license-id]
           [:license-ref  :license-ref]
           [:special-form :special-form])
            (sl/equivalent? s1 s2)

          ([:exception-id :exception-id]
           [:addition-ref :addition-ref])
            (se/equivalent? s1 s2)

          false))))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn info
  "Returns SPDX list information for `id` as a map, or `nil` if `id` is not a
  valid SPDX identifier. Includes a `:type` element that identifies whether the
  id is an SPDX license identifier or an SPDX exception identifier, as per
  [[id-type]].

  `opts` are:

  * `:include-large-text-values?` (default `false`) - controls whether large text
    values are included in the result or not"
  ([^String id] (info id nil))
  ([^String id {:keys [include-large-text-values?] :or {include-large-text-values? false} :as opts}]
   (when-let [id-t (id-type id)]
     (case id-t
       :license-id   (assoc (sl/info id opts) :type id-t)
       :exception-id (assoc (se/info id opts) :type id-t)
       nil))))

(defn deprecated?
  "Is `id` (a `String`) deprecated?  Also returns `false` if `id` is not an SPDX
  listed identifier (including for LicenseRefs and AdditionRefs).

  See [this SPDX FAQ item](https://github.com/spdx/license-list-XML/blob/main/DOCS/faq.md#what-does-it-mean-when-a-license-id-is-deprecated)
  for details on what this means."
  [^String id]
  (boolean
    (when-let [info (info id)]
      (:deprecated? info))))

(defn non-deprecated-ids
  "Returns the set of SPDX identifiers that identify current (non-deprecated)
  licenses within the provided set of SPDX identifiers (or all of them, if `ids`
  is not provided)."
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
  (sl/init!)
  (se/init!)
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

(defn ^:deprecated ^:no-doc id->info
  "Superceded by [[info]]"
  ([^String id]      (info id))
  ([^String id opts] (info id opts)))

(defn ^:deprecated ^:no-doc deprecated-id?
  "Superceded by [[deprecated?]]."
  [^String s]
  (deprecated? s))
