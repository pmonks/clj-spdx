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
            [spdx.licenses     :as slic]
            [spdx.exceptions   :as sexc]
            [spdx.impl.regexes :as ir]))

(defn- sort-by-count-desc
  "Sorts `coll`, a sequence of `String`s, by the length of each entry, in
  descending order (so longer values come first).  Returns `nil` if `coll` is
  `nil` or `empty`."
  [coll]
  (when (seq coll)
    (reverse (sort-by count coll))))

#_{:clj-kondo/ignore [:unused-binding {:exclude-destructured-keys-in-fn-args true}]}
(defn build-re
  "Returns a regex (`Pattern`) that will match the given `ids`, or `nil` if
  `ids` is `nil` or empty.  `ids` appear sorted longest to shortest in the regex,
  so that more specific values are preferentially matched - this avoids
  mismatches when one id is a subset of another id (e.g. `GPL-2.0` and
  `GPL-2.0-or-later`).

  `opts` are:

  * `match-license-refs?` (default `false`) - controls whether LicenseRef
    matching is also included in the regex
  * `match-addition-refs?` (default `false`) - controls whether AdditionRef
    matching is also included in the regex

  Note:

  * unlike other fns in this ns, this one returns a new `Pattern` object on
    every invocation, even if the args are the same as a previous one"
  ([ids] (build-re ids nil))
  ([ids {:keys [match-license-refs? match-addition-refs?]
         :or   {match-license-refs?  false
                match-addition-refs? false}
         :as   opts}]
   (when (seq ids)
     (ir/re-concat #"(?i)(\A|\b)"
                   "(?<Identifier>"
                   (when match-license-refs? (str @ir/license-ref-re-d "|"))
                   (when match-addition-refs? (str @ir/addition-ref-re-d "|"))
                   (s/join "|" (map ir/re-escape (sort-by-count-desc ids)))
                   ")"
                   #"(\b|\z)"))))

(def ^:private ids-re-d (delay (build-re (concat (slic/ids) (sexc/ids)) {:match-license-refs? true :match-addition-refs? true})))

(defn ids-re
  "Returns a regex (`Pattern`) that matches any SPDX license identifier,
  exception identifier, LicenseRef, or AdditionRef.  The regex provides these
  named capturing groups:

  * `Identifier` (always present) - matches the entire identifier
  * `DocumentRef` (optional) - matches the DocumentRef tag of a LicenseRef, if
    it contains one
  * `LicenseRef` (optional) - matches the LicenseRef tag of a LicenseRef
  * `AdditionDocumentRef` (optional) - matches the DocumentRef tag of an
    AdditionRef, if it contains one
  * `AdditionRef` (optional) - matches the AdditionRef tag of an AdditionRef

  Notes:

  * returns the same `Pattern` object on subsequent calls, so is efficient when
    called many times"
  []
  @ids-re-d)

(def ^:private license-ids-re-d (delay (build-re (slic/ids) {:match-license-refs? true})))

(defn license-ids-re
  "Returns a regex (`Pattern`) that matches any SPDX license identifier or
  LicenseRef.  The regex provides these named capturing groups:

  * `Identifier` (always present) - matches the entire identifier
  * `DocumentRef` (optional) - matches the DocumentRef tag of a LicenseRef, if
    it contains one
  * `LicenseRef` (optional) - matches the LicenseRef tag of a LicenseRef

  Notes:

  * returns the same `Pattern` object on subsequent calls, so is efficient when
    called many times"
  []
  @license-ids-re-d)

(def ^:private exception-ids-re-d (delay (build-re (sexc/ids) {:match-addition-refs? true})))

(defn exception-ids-re
  "Returns a regex (`Pattern`) that matches any SPDX exception identifier or
  AdditionRef.  The regex provides these named capturing groups:

  * `Identifier` (always present) - matches the entire identifier
  * `AdditionDocumentRef` (optional) - matches the DocumentRef tag of an
    AdditionRef, if it contains one
  * `AdditionRef` (optional) - matches the AdditionRef tag of an AdditionRef

  Notes:

  * returns the same `Pattern` object on subsequent calls, so is efficient when
    called many times"
  []
  @exception-ids-re-d)

(def ^:private license-ref-re-d (delay (ir/re-concat #"(?i)(\A|\b)"
                                                     @ir/license-ref-re-d
                                                     #"(\b|\z)")))

(defn license-ref-re
  "Returns a regex (`Pattern`) that matches any SPDX LicenseRef.  The regex
  provides these named capturing groups:

  * `DocumentRef` (optional) - matches the DocumentRef tag of a LicenseRef, if
    it contains one
  * `LicenseRef` (always present) - matches the LicenseRef tag of a LicenseRef

  Notes:

  * returns the same `Pattern` object on subsequent calls, so is efficient when
    called many times"
  []
  @license-ref-re-d)

(def ^:private addition-ref-re-d (delay (ir/re-concat #"(?i)(\A|\b)"
                                                      @ir/addition-ref-re-d
                                                      #"(\b|\z)")))

(defn addition-ref-re
 "Returns a regex (`Pattern`) that matches any SPDX AdditionRef.  The regex
 provides these named capturing groups:

  * `AdditionDocumentRef` (optional) - matches the DocumentRef tag of an
    AdditionRef, if it contains one
  * `AdditionRef` (always present) - matches the AdditionRef tag of an
    AdditionRef

  Notes:
  * returns the same `Pattern` object on subsequent calls, so is efficient when
    called many times"
  []
  @addition-ref-re-d)

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this method may have a substantial performance cost."
  []
  (slic/init!)
  (sexc/init!)
  (ir/init!)
  @ids-re-d
  @license-ids-re-d
  @exception-ids-re-d
  @license-ref-re-d
  @addition-ref-re-d
  nil)
