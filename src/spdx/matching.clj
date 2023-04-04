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
  "License matching functionality, primarily provided by org.spdx.utility.compare.LicenseCompareHelper."
  (:require [spdx.impl.mapping :as im]))

(defn text-is-license?
  "Does the entire text match the license?"
  [^String text ^String license-id]
  (when (and text license-id)
    (not (.isDifferenceFound (org.spdx.utility.compare.LicenseCompareHelper/isTextStandardLicense (im/id->license license-id) text)))))

(defn text-is-exception?
  "Does the entire text match the exception?"
  [^String text ^String exception-id]
  (when (and text exception-id)
    (not (.isDifferenceFound (org.spdx.utility.compare.LicenseCompareHelper/isTextStandardException (im/id->exception exception-id) text)))))

(defn text-contains-license?
  "Does the text contain the license somewhere within it?"
  [^String text ^String license-id]
  (when (and text license-id)
    (org.spdx.utility.compare.LicenseCompareHelper/isStandardLicenseWithinText text (im/id->license license-id))))

(defn text-contains-exception?
  "Does the text contain the exception somewhere within it?"
  [^String text ^String exception-id]
  (when (and text exception-id)
    (org.spdx.utility.compare.LicenseCompareHelper/isStandardLicenseExceptionWithinText text (im/id->exception exception-id))))

(defn texts-equivalent-licenses?
  "Do the two texts represent equivalent licenses? Note: there is no equivalent function for exceptions."
  [^String text1 ^String text2]
  (when (and text1 text2)
    (org.spdx.utility.compare.LicenseCompareHelper/isLicenseTextEquivalent text1 text2)))

(defn licenses-within-text
  "Returns the set of ids for all licenses found in the given text (optionally from the provided list of license ids), or nil if none were found."
  ([^String text]
   (when text
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseIdsWithinText text))
             set)))
  ([^String text license-ids]
   (when (and text (seq license-ids))
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseIdsWithinText text (seq license-ids)))
             set))))

(defn exceptions-within-text
  "Returns the set of ids for all exceptions found in the given text (optionally from the provided set of exception ids), or nil if none were found."
  ([^String text]
   (when text
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseExceptionIdsWithinText text))
             set)))
  ([^String text exception-ids]
   (when (and text (seq exception-ids))
     (some-> (seq (org.spdx.utility.compare.LicenseCompareHelper/matchingStandardLicenseExceptionIdsWithinText text (seq exception-ids)))
             set))))
