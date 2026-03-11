;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.matching
  "License matching functionality, primarily provided by `org.spdx.utility.compare.LicenseCompareHelper`."
  (:require [spdx.licenses     :as sl]
            [spdx.exceptions   :as se]
            [spdx.impl.mapping :as sim]))

(defn text-is-license?
  "Does the entire `text` match the license identified by `license-id`?"
  [^String text ^String license-id]
  (if (and text license-id)
    (if-let [lic (sim/id->license (sl/canonicalise license-id))]
      (not (.isDifferenceFound (org.spdx.utility.compare.LicenseCompareHelper/isTextStandardLicense lic text)))
      false)
    false))

(defn text-is-exception?
  "Does the entire `text` match the exception identified by `exception-id`?"
  [^String text ^String exception-id]
  (if (and text exception-id)
    (if-let [exc (sim/id->exception (se/canonicalise exception-id))]
      (not (.isDifferenceFound (org.spdx.utility.compare.LicenseCompareHelper/isTextStandardException exc text)))
      false)
    false))

(defn text-contains-license?
  "Does the `text` contain the license identified by `license-id` somewhere within it?"
  [^String text ^String license-id]
  (if (and text license-id)
    (if-let [lic (sim/id->license (sl/canonicalise license-id))]
      (org.spdx.utility.compare.LicenseCompareHelper/isStandardLicenseWithinText text lic)
      false)
    false))

(defn text-contains-exception?
  "Does the `text` contain the exception identified by `exception-id` somewhere within it?"
  [^String text ^String exception-id]
  (if (and text exception-id)
    (if-let [exc (sim/id->exception (se/canonicalise exception-id))]
      (org.spdx.utility.compare.LicenseCompareHelper/isStandardLicenseExceptionWithinText text exc)
      false)
    false))

(defn texts-equivalent-licenses?
  "Does `text1` and `text2` represent an equivalent license?"
  [^String text1 ^String text2]
  (if (and text1 text2)
    (org.spdx.licenseTemplate.LicenseTextHelper/isLicenseTextEquivalent text1 text2)
    (= nil text1 text2)))   ; Two nil texts are considered equivalent

(defn texts-equivalent-exceptions?
  "Does `text1` and `text2` represent an equivalent exception?"
  [^String text1 ^String text2]
  (texts-equivalent-licenses? text1 text2))    ; Spdx-Java-Library doesn't provide a separate API for exception text comparison, but the comparison logic is the same as for licenses

(defn licenses-within-text
  "Returns the set of ids for all licenses found in `text` (optionally limited
  to just the provided set of `license-ids`), or `nil` if none were found.

  Note: this function has a substantial performance cost. Callers are encouraged
  to break their ids into batches and call the 2-arg version with each batch
  in parallel (e.g. using `clojure.core/pmap`), then merge the results."
  ([^String text]
   (when text
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseIdsWithinText text))
             set)))
  ([^String text license-ids]
   (when (and text (seq license-ids))
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseIdsWithinText text (seq (map sl/canonicalise license-ids))))
             set))))

(defn exceptions-within-text
  "Returns the set of ids for all exceptions found in `text` (optionally limited
  to just the provided set of `exception-ids`), or `nil` if none were found.

  Note: this function has a substantial performance cost. Callers are encouraged
  to break their ids into batches and call the 2-arg version with each batch
  in parallel (e.g. using `clojure.core/pmap`), then merge the results."
  ([^String text]
   (when text
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseExceptionIdsWithinText text))
             set)))
  ([^String text exception-ids]
   (when (and text (seq exception-ids))
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseExceptionIdsWithinText text (seq (map se/canonicalise exception-ids))))
             set))))

(defn differences
  "Returns a map representing the differences found when attempting to match
  `text` against the SPDX matching template for `license-or-exception-id`.
  Returns `nil` if `text` or `license-or-exception-id` are `nil`, when
  `license-or-exception-id` is invalid (does not refer to a listed SPDX license
  or license exception), or when no differences were found (note that this last
  part is unlike the underlying Java library, which returns a non-nil 'no
  differences found' object in this case)."
  [^String text ^String license-or-exception-id]
  (when (and text license-or-exception-id)
    (when-let [difference (sim/difference-description->map
                            (if-let [lic (sim/id->license (sl/canonicalise license-or-exception-id))]
                              (org.spdx.utility.compare.LicenseCompareHelper/isTextStandardLicense lic text)
                              (when-let [exc (sim/id->exception (se/canonicalise license-or-exception-id))]
                                (org.spdx.utility.compare.LicenseCompareHelper/isTextStandardException exc text))))]
      (when (:differences-found? difference)
        difference))))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this function may have a substantial performance cost."
  []
  (sl/init!)
  (se/init!)
  (sim/init!)
  nil)
