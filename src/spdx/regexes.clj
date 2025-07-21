;
; Copyright © 2024 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.regexes
  "Regex related functionality.  This functionality is bespoke (it does not use
  any logic from `Spdx-Java-Library`)."
  (:require [clojure.string    :as s]
            [wreck.api         :as re]
            [rencg.api         :as rencg]
            [spdx.licenses     :as lic]
            [spdx.exceptions   :as exc]
            [spdx.impl.regexes :as ir]))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn build-re
  "Returns a regex (`Pattern`) that can find or match any one of the given SPDX
  `ids` (a sequence of `String`s) in a source text. Returns `nil` if `ids` is
  `nil` or empty.

  The regex includes these named capturing groups:

  * `Identifier` (always present) - captures the entire identifier, `LicenseRef`
    or `AdditionRef`
  * `DocumentRef` (optional) - captures the `DocumentRef` variable text of a
    `LicenseRef`, if that's what's matched and it contains one
  * `LicenseRef` (optional) - captures the `LicenseRef` variable textof a
    `LicenseRef`, if that's what's matched
  * `AdditionDocumentRef` (optional) - captures the `DocumentRef` variable text
    of an `AdditionRef`, if that's what's matched and it contains one
  * `AdditionRef` (optional) - captures the `AdditionRef` variable text of an
    `AdditionRef`, if that's what's matched

  Groups should _not_ be accessed by index, as the groups in the returned
  regexes are not part of the public contract of this API, and are liable to
  change over time.  You may choose to use something like
  [rencg](https://github.com/pmonks/rencg) (a library that clj-spdx has a
  dependency upon, so is already available to your code) to ensure your code is
  future proof in this regard.

  `ids` will appear in the regex sorted from longest to shortest, so that more
  specific values are preferentially found or matched first - this avoids
  mismatches when one id is a subset of another id (e.g. `GPL-2.0-or-later` and
  `GPL-2.0`).

  `opts` are:

  * `case-sensitive?` (`boolean`, default `false`) - controls whether SPDX
    identifier matching is case sensitive or not. The [spec explicitly states
    that SPDX identifiers are _not_ case sensitive](https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity),
    but there may be cases where case sensitive matching is preferred.  Note
    that regardless of this setting, LicenseRefs and AdditionRefs (if included)
    are _always_ matched as required by the spec (i.e. the constant 'tag'
    sections are matched case-sensitively, and the 'variable text' sections are
    not)
  * `include-license-refs?` (`boolean`, default `false`) - controls whether
    `LicenseRef` support is also included in the regex
  * `include-addition-refs?` (`boolean`, default `false`) - controls whether
    `AdditionRef` support is also included in the regex"
  ([ids] (build-re ids nil))
  ([ids {:keys [case-sensitive?
                include-license-refs?
                include-addition-refs?]
         :or   {case-sensitive?        false
                include-license-refs?  false
                include-addition-refs? false}
         :as   opts}]
   (when (seq ids)
     (re/join #"(?<!\w)"
              "(?<Identifier>"
              (when include-license-refs?  (str @ir/license-ref-fragment-re-d "|"))
              (when include-addition-refs? (str @ir/addition-ref-fragment-re-d "|"))
              (when-not case-sensitive? #"(?i)")  ; Only disable case sensitivity _after_ LicenseRefs and AdditionRefs, as they're always case sensitive (see https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity)
              (s/join "|" (map re/esc (sort-by #(* -1 (count %)) ids)))  ; Sort longest to shortest
              ")"
              #"(?!\w)"))))

(def ^:private ids-re-d (delay (build-re (concat (lic/ids) (exc/ids)) {:case-sensitive? false :include-license-refs? true :include-addition-refs? true})))

(defn ids-re
  "Returns a regex (`Pattern`) that can find or match any SPDX license
  identifier, SPDX exception identifier, `LicenseRef`, or `AdditionRef` in
  a source text.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @ids-re-d)

(def ^:private license-ids-re-d (delay (build-re (lic/ids) {:case-sensitive? false :include-license-refs? true :include-addition-refs? false})))

(defn license-ids-re
  "Returns a regex (`Pattern`) that can find or match any SPDX license
  identifier, or `LicenseRef` in a source text.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @license-ids-re-d)

(def ^:private exception-ids-re-d (delay (build-re (exc/ids) {:case-sensitive? false :include-license-refs? false :include-addition-refs? true})))

(defn exception-ids-re
  "Returns a regex (`Pattern`) that can find or match any SPDX license exception
  identifier, or `AdditionRef` in a source text.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @exception-ids-re-d)

(defn license-ref-re
  "Returns a regex (`Pattern`) that can find or match any SPDX `LicenseRef`.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @ir/license-ref-re-d)

(defn addition-ref-re
 "Returns a regex (`Pattern`) that can find or match any SPDX `AdditionRef`.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @ir/addition-ref-re-d)

(defn- id-type
  "Returns a keyword representing the 'type' of `id`:

  * `:license-id` - it's a listed license identifier
  * `:exception-id` - it's a listed exception identifier
  * `:license-ref` - it's a LicenseRef
  * `:addition-ref` - it's an AdditionRef"
  [^String id]
  (when id
    (cond
      (lic/listed-id? id)    :license-id
      (exc/listed-id? id)    :exception-id
      (lic/license-ref? id)  :license-ref
      (exc/addition-ref? id) :addition-ref
      :else                  nil)))

(defn- canonicalise-id
  "Canonicalises `id` (if it's a listed license or exception identifer), or
  returns it verbatim if it is not (i.e. it's a LicenseRef or AdditionRef)."
  [^String id]
  (when id
    (if-let [canonical-license-id (lic/canonicalise-id id)]
      canonical-license-id
      (if-let [canonical-exception-id (exc/canonicalise-id id)]
        canonical-exception-id
        id))))

(defn id-seq-matches
  "Returns a lazy sequence of maps representing each of the identifier matches
  found in `text`, in the order in which they were found, or `nil` if no matches
  were found. `re` must be a regex returned by one of the fns in this namespace,
  and defaults to [[ids-re]] if not provided.

  The result is as for [rencg.api/re-seq-ncg](https://pmonks.github.io/rencg/rencg.api.html#var-re-seq-ncg)
  and each map contains the named capture groups described in [[build-re]],
  plus:

  * `:identifier` (always present) - the canonical represention of the listed
    identifier that matched, or the verbatim `LicenseRef` or `AdditionRef` that
    matched
  * `:type` (always present) - one of `:license-id`, `:exception-id`,
    `:license-ref`, or `:addition-ref`"
  ([^String text] (id-seq-matches @ids-re-d text))
  ([^java.util.regex.Pattern re ^String text]
   (when (and re text)
     (when-let [matches (rencg/re-seq-ncg re text)]
       (seq (map #(assoc % :identifier (canonicalise-id (get % "Identifier"))
                           :type       (id-type         (get % "Identifier")))
                 matches))))))

(defn id-seq
  "Returns a lazy sequence of the canonicalised forms of all identifiers found
  in `text`, in the order in which they were found, or `nil` if no matches were
  found. `re` must be a regex returned by one of the fns in this namespace, and
  defaults to [[ids-re]] if not provided.

  If you need more information about where in the text the identifiers were
  found, or the original text that matched an identifier, use [[id-seq-matches]]
  instead."
  ([^String text] (id-seq @ids-re-d text))
  ([^java.util.regex.Pattern re ^String text]
   (seq (map :identifier (id-seq-matches re text)))))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this function may have a substantial performance cost."
  []
  (lic/init!)
  (exc/init!)
  (ir/init!)
  ; Note: we always lazy-initialise all of the regexes, as it's unlikely that
  ; a caller will use all of them, and they're quick to construct. This saves
  ; callers unecessary memory consumption (an unrealised delay, while not free,
  ; consumes very little memory - 96 bytes on my machine).
  nil)
