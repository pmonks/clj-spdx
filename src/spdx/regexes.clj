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
            [spdx.licenses     :as slic]
            [spdx.exceptions   :as sexc]
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
              (when include-license-refs? (str @ir/license-ref-re-d "|"))
              (when include-addition-refs? (str @ir/addition-ref-re-d "|"))
              (when-not case-sensitive? #"(?i)")  ; Only disable case sensitivity _after_ LicenseRefs and AdditionRefs, as they're always case sensitive (see https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity)
              (s/join "|" (map re/esc (sort-by #(* -1 (count %)) ids)))  ; Sort longest to shortest
              ")"
              #"(?!\w)"))))

(def ^:private ids-re-d (delay (build-re (concat (slic/ids) (sexc/ids)) {:case-sensitive? false :include-license-refs? true :include-addition-refs? true})))

(defn ids-re
  "Returns a regex (`Pattern`) that can find or match any SPDX license
  identifier, SPDX exception identifier, `LicenseRef`, or `AdditionRef` in
  a source text.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @ids-re-d)

(def ^:private license-ids-re-d (delay (build-re (slic/ids) {:case-sensitive? false :include-license-refs? true :include-addition-refs? false})))

(defn license-ids-re
  "Returns a regex (`Pattern`) that can find or match any SPDX license
  identifier, or `LicenseRef` in a source text.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @license-ids-re-d)

(def ^:private exception-ids-re-d (delay (build-re (sexc/ids) {:case-sensitive? false :include-license-refs? false :include-addition-refs? true})))

(defn exception-ids-re
  "Returns a regex (`Pattern`) that can find or match any SPDX license exception
  identifier, or `AdditionRef` in a source text.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @exception-ids-re-d)


; Note: the DocumentRef and LicenseRef portions of a LicenseRef are case-sensitive (see https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity)
(def ^:private license-ref-re-d (delay (re/join #"(?<!\w)"
                                                @ir/license-ref-re-d
                                                #"(?!\w)")))

(defn license-ref-re
  "Returns a regex (`Pattern`) that can find or match any SPDX `LicenseRef`.
  The regex provides these named capturing groups:

  * `DocumentRef` (optional) - captures the `DocumentRef` variable text of a
    `LicenseRef`, if it contains one
  * `LicenseRef` (always present) - captures the `LicenseRef` variable text of a
    `LicenseRef`

  Notes:

  * caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @license-ref-re-d)

; Note: the DocumentRef and AdditionRef portions of an AdditionRef are case-sensitive (see https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/#case-sensitivity)
(def ^:private addition-ref-re-d (delay (re/join #"(?<!\w)"
                                                 @ir/addition-ref-re-d
                                                 #"(?!\w)")))

(defn addition-ref-re
 "Returns a regex (`Pattern`) that can find or match any SPDX `AdditionRef`.
 The regex provides these named capturing groups:

  * `AdditionDocumentRef` (optional) - captures the `DocumentRef` variable text
    of an `AdditionRef`, if it contains one
  * `AdditionRef` (always present) - captures the `AdditionRef` variable text of
    an `AdditionRef`

  Notes:

  * caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @addition-ref-re-d)

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this function may have a substantial performance cost."
  []
  (slic/init!)
  (sexc/init!)
  (ir/init!)
  ; Note: we always lazy-initialise all of the regexes, as it's unlikely that
  ; a caller will use all of them, and they're quick to construct. This saves
  ; callers unecessary memory consumption.
  nil)
