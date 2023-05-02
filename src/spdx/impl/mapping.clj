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

(ns spdx.impl.mapping
  "Java object mapping namespace. Note: this namespace is not part of the public API of clj-spdx and may change without notice."
  (:require [clojure.string  :as s]
            [clojure.instant :as inst]
            [spdx.impl.state :as is]))

(defn- unwrap-optional
  "Because Java is becoming increasingly unhinged... 🙄"
  [x]
  (if (= java.util.Optional (type x))
    (.orElse ^java.util.Optional x nil)
    x))

(defn- value-to-map
  "Returns value in a singleton map with the given key, or nil if value is nil."
  ([k v] (value-to-map k v nil))
  ([k v f]
   (when-let [v ((or f identity) (unwrap-optional v))]
     {k v})))

(defn- nil-blank-string
  "Returns s, or nil if it is blank."
  [^String s]
  (when-not (s/blank? s)
    s))

(defn- read-instant-date
  "Because clojure.instant/read-instant-date isn't nil tolerant... 🙄"
  [^String s]
  (when s
    (inst/read-instant-date s)))

(defn listed-license-id?
  "Is the given id one of the listed SPDX license identifiers?"
  [^String id]
  (when id
    (.isSpdxListedLicenseId ^org.spdx.library.model.license.ListedLicenses @is/list-obj id)))

(defn listed-exception-id?
  "Is the given id one of the listed SPDX exception identifiers?"
  [^String id]
  (when id
    (.isSpdxListedExceptionId ^org.spdx.library.model.license.ListedLicenses @is/list-obj id)))

(defn cross-ref->map
  "Turns a org.spdx.library.model.license.CrossRef object into a map. All map
  keys are optional, but may include:
  :is-wayback-link? - boolean
  :live?            - boolean
  :match            - string
  :order            - integer
  :timestamp        - string
  :url              - string
  :valid?           - boolean

  See https://spdx.github.io/Spdx-Java-Library/org/spdx/library/model/license/CrossRef.html
  for more information."
  ^java.util.Map [^org.spdx.library.model.license.CrossRef cr]
  (when cr
    (merge (value-to-map :is-wayback-link? (.getIsWayBackLink cr))
           (value-to-map :live?            (.getLive          cr))
           (value-to-map :match            (.getMatch         cr))
           (value-to-map :order            (.getOrder         cr))
           (value-to-map :timestamp        (.getTimestamp     cr) read-instant-date)
;           (value-to-map :type             (.getType          cr))    ; Spdx-Java-Library implementation detail
           (value-to-map :url              (.getUrl           cr) nil-blank-string)
           (value-to-map :valid?           (.getValid         cr)))))

(defn license->map
  "Turns a org.spdx.library.model.license.SpdxListedLicense object into a map.
  All map keys are optional, but may include:
  :id                 - string (an SPDX identifier)
  :name               - string
  :comment            - string
  :see-also           - sequence of strings
  :cross-refs         - sequence of maps (see cross-ref->map for details)
  :deprecated?        - boolean
  :deprecated-version - string
  :fsf-libre?         - boolean
  :osi-approved?      - boolean
  :text               - string
  :text-html          - string
  :text-template      - string
  :header             - string
  :header-html        - string
  :header-template    - string

  See https://spdx.github.io/Spdx-Java-Library/org/spdx/library/model/license/SpdxListedLicense.html
  for more information."
  ^java.util.Map [^org.spdx.library.model.license.SpdxListedLicense lic]
  (when lic
    (merge (value-to-map :id                 (.getLicenseId                     lic))
           (value-to-map :name               (.getName                          lic) nil-blank-string)
;           (value-to-map :type               (.getType                          lic))    ; Spdx-Java-Library implementation detail
           (value-to-map :comment            (.getComment                       lic) nil-blank-string)
           (value-to-map :see-also           (seq (.getSeeAlso                  lic)))
           (value-to-map :cross-refs         (seq (filter identity (map cross-ref->map (.getCrossRef lic)))))
           (value-to-map :deprecated?        (.isDeprecated                     lic) boolean)
           (value-to-map :deprecated-version (.getDeprecatedVersion             lic) nil-blank-string)
           (value-to-map :fsf-libre?         (.getFsfLibre                      lic))
           (value-to-map :osi-approved?      (.isOsiApproved                    lic))
           (value-to-map :text               (.getLicenseText                   lic) nil-blank-string)
           (value-to-map :text-html          (.getLicenseTextHtml               lic) nil-blank-string)
           (value-to-map :text-template      (.getStandardLicenseTemplate       lic) nil-blank-string)
           (value-to-map :header             (.getStandardLicenseHeader         lic) nil-blank-string)
           (value-to-map :header-html        (.getLicenseHeaderHtml             lic) nil-blank-string)
           (value-to-map :header-template    (.getStandardLicenseHeaderTemplate lic) nil-blank-string))))

(defn id->license
  "Turns a valid license id into a org.spdx.library.model.license.SpdxListedLicense
  object, or returns nil. Note: unlike the underlying Java library it only
  handles listed SPDX license ids."
  ^org.spdx.library.model.license.SpdxListedLicense [^String id]
  (when (listed-license-id? id)
    (.getListedLicenseById ^org.spdx.library.model.license.ListedLicenses @is/list-obj id)))

(defn exception->map
  "Turns a org.spdx.library.model.license.ListedLicenseException object into a
  map. All map keys are optional, but may include:
  :id                 - string (an SPDX identifier)
  :name               - string
  :comment            - string
  :see-also           - sequence of strings
  :deprecated?        - boolean
  :deprecated-version - string
  :text               - string
  :text-html          - string
  :text-template      - string

  See https://spdx.github.io/Spdx-Java-Library/org/spdx/library/model/license/ListedLicenseException.html
  for more information."
  ^java.util.Map [^org.spdx.library.model.license.ListedLicenseException exc]
  (when exc
    (merge (value-to-map :id                 (.getLicenseExceptionId exc))
           (value-to-map :name               (.getName exc))
;           (value-to-map :type               (.getType exc))    ; Spdx-Java-Library implementation detail
           (value-to-map :comment            (.getComment exc)                  nil-blank-string)
           (value-to-map :see-also           (seq (.getSeeAlso exc)))
           (value-to-map :deprecated?        (.isDeprecated exc)                boolean)
           (value-to-map :deprecated-version (.getDeprecatedVersion exc)        nil-blank-string)
           (value-to-map :text               (.getLicenseExceptionText exc)     nil-blank-string)
           (value-to-map :text-html          (.getExceptionTextHtml exc)        nil-blank-string)
           (value-to-map :text-template      (.getLicenseExceptionTemplate exc) nil-blank-string))))

(defn id->exception
  "Turns a valid exception id into a org.spdx.library.model.license.ListedLicenseException
  object, or returns nil. Note: unlike the underlying Java library it only
  handles listed SPDX exception ids."
  ^org.spdx.library.model.license.ListedLicenseException [^String id]
  (when (listed-exception-id? id)
    (.getListedExceptionById ^org.spdx.library.model.license.ListedLicenses @is/list-obj id)))
