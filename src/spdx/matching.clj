;
; Copyright © 2023 Peter Monks
;
; Licensed under the Apache License Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;
; SPDX-License-Identifier: Apache-2.0
;

(ns spdx.matching
  "License matching functionality, primarily provided by `org.spdx.utility.compare.LicenseCompareHelper`."
  (:require [spdx.impl.mapping :as im]))

(defn text-is-license?
  "Does the entire `text` match the license identified by `license-id`?"
  [^String text ^String license-id]
  (if (and text license-id)
    (if-let [lic (im/id->license license-id)]
      (not (.isDifferenceFound (org.spdx.utility.compare.LicenseCompareHelper/isTextStandardLicense lic text)))
      false)
    false))

(defn text-is-exception?
  "Does the entire `text` match the exception identified by `exception-id`?"
  [^String text ^String exception-id]
  (if (and text exception-id)
    (if-let [exc (im/id->exception exception-id)]
      (not (.isDifferenceFound (org.spdx.utility.compare.LicenseCompareHelper/isTextStandardException exc text)))
      false)
    false))

(defn text-contains-license?
  "Does the `text` contain the license identified by `license-id` somewhere within it?"
  [^String text ^String license-id]
  (if (and text license-id)
    (if-let [lic (im/id->license license-id)]
      (org.spdx.utility.compare.LicenseCompareHelper/isStandardLicenseWithinText text lic)
      false)
    false))

(defn text-contains-exception?
  "Does the `text` contain the exception identified by `exception-id` somewhere within it?"
  [^String text ^String exception-id]
  (if (and text exception-id)
    (if-let [exc (im/id->exception exception-id)]
      (org.spdx.utility.compare.LicenseCompareHelper/isStandardLicenseExceptionWithinText text exc)
      false)
    false))

(defn texts-equivalent-licenses?
  "Does `text1` and `text2` represent an equivalent license?"
  [^String text1 ^String text2]
  (if (and text1 text2)
    (org.spdx.utility.compare.LicenseCompareHelper/isLicenseTextEquivalent text1 text2)
    (= nil text1 text2)))   ; Two nil texts are considered equivalent

(defn texts-equivalent-exceptions?
  "Does `text1` and `text2` represent an equivalent exception?"
  [^String text1 ^String text2]
  (texts-equivalent-licenses? text1 text2))    ; Spdx-Java-Library doesn't provide a separate API for exception text comparison, but the comparison logic is the same as for licenses

(defn licenses-within-text
  "Returns the set of ids for all licenses found in `text` (optionally limited
  to just the provided set of `license-ids`), or `nil` if none were found.

  Note: this method has a substantial performance cost. Callers are encouraged
  to break their ids into batches and call the 2-arg version with each batch
  in parallel (e.g. using `clojure.core/pmap`), then merge the results."
  ([^String text]
   (when text
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseIdsWithinText text))
             set)))
  ([^String text license-ids]
   (when (and text (seq license-ids))
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseIdsWithinText text (seq license-ids)))
             set))))

(defn exceptions-within-text
  "Returns the set of ids for all exceptions found in `text` (optionally limited
  to just the provided set of `exception-ids`), or `nil` if none were found.

  Note: this method has a substantial performance cost. Callers are encouraged
  to break their ids into batches and call the 2-arg version with each batch
  in parallel (e.g. using `clojure.core/pmap`), then merge the results."
  ([^String text]
   (when text
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseExceptionIdsWithinText text))
             set)))
  ([^String text exception-ids]
   (when (and text (seq exception-ids))
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseExceptionIdsWithinText text (seq exception-ids)))
             set))))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it.

  Note: this method may have a substantial performance cost."
  []
  (im/init!)
  nil)
