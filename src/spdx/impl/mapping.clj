;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.impl.mapping
  "Java object mapping namespace. Note: this namespace is not part of the public
  API of clj-spdx and may change without notice."
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
  "Returns `v` in a singleton map with key `k`, or `nil` if `v` is
  `nil`. Unwraps `java.util.Optional` values, and when `f` is provided
  applies it to `v` before putting it in the result."
  ([k v] (value-to-map k v nil))
  ([k v f]
   (when-let [v ((or f identity) (unwrap-optional v))]
     {k v})))

(defn- nil-blank-string
  "Returns s, or nil if it is blank."
  [^String s]
  (when-not (s/blank? s) s))

(defn- read-instant-date
  "Because clojure.instant/read-instant-date isn't nil tolerant... 🙄"
  [^String s]
  (when s (inst/read-instant-date s)))

(defn listed-license-id?
  "Is the given id one of the listed SPDX license identifiers?"
  [^String id]
  (boolean (when id (.isSpdxListedLicenseId ^org.spdx.library.model.license.ListedLicenses @is/list-obj id))))

(defn listed-exception-id?
  "Is the given id one of the listed SPDX exception identifiers?"
  [^String id]
  (boolean (when id (.isSpdxListedExceptionId ^org.spdx.library.model.license.ListedLicenses @is/list-obj id))))

(defn cross-ref->map
  "Turns a `org.spdx.library.model.license.CrossRef` object into a map. All map
  keys are optional, but may include:

  * `:is-wayback-link?` - `boolean`
  * `:live?`            - `boolean`
  * `:match`            - `String`
  * `:order`            - `Integer`
  * `:timestamp`        - `String`
  * `:url`              - `String`
  * `:valid?`           - `boolean`

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
  "Turns a `org.spdx.library.model.license.SpdxListedLicense` object into a map.
  All map keys are optional, but may include:

  * `:id`                 - `String` (an SPDX identifier)
  * `:name`               - `String`
  * `:comment`            - `String`
  * `:see-also`           - sequence of `String`s
  * `:cross-refs`         - sequence of maps (see cross-ref->map for details)
  * `:deprecated?`        - `boolean`
  * `:deprecated-version` - `String`
  * `:fsf-libre?`         - `boolean`
  * `:osi-approved?`      - `boolean`
  * `:text`               - `String`
  * `:text-html`          - `String`
  * `:text-template`      - `String`
  * `:header`             - `String`
  * `:header-html`        - `String`
  * `:header-template`    - `String`

  See https://spdx.github.io/Spdx-Java-Library/org/spdx/library/model/license/SpdxListedLicense.html
  for more information.

  `opts` are:

  * `:include-large-text-values?` (default `false`) - controls whether the
    following large text values are included in the result: `:comment :text
    :text-html :text-template :header :header-html :header-template`"
  ^java.util.Map [^org.spdx.library.model.license.SpdxListedLicense lic
                  {:keys [include-large-text-values?] :or {include-large-text-values? false}}]
  (when lic
    (merge (value-to-map :id                 (.getLicenseId                     lic))
           (value-to-map :name               (.getName                          lic) nil-blank-string)
;           (value-to-map :type               (.getType                          lic))    ; Spdx-Java-Library implementation detail
           (value-to-map :see-also           (seq (.getSeeAlso                  lic)))
           (value-to-map :cross-refs         (seq (filter identity (map cross-ref->map (.getCrossRef lic)))))
           (value-to-map :deprecated?        (.isDeprecated                     lic) boolean)
           (value-to-map :deprecated-version (.getDeprecatedVersion             lic) nil-blank-string)
           (value-to-map :fsf-libre?         (.getFsfLibre                      lic))
           (value-to-map :osi-approved?      (.isOsiApproved                    lic))
           (when include-large-text-values? (value-to-map :comment            (.getComment                       lic) nil-blank-string))
           (when include-large-text-values? (value-to-map :text               (.getLicenseText                   lic) nil-blank-string))
           (when include-large-text-values? (value-to-map :text-html          (.getLicenseTextHtml               lic) nil-blank-string))
           (when include-large-text-values? (value-to-map :text-template      (.getStandardLicenseTemplate       lic) nil-blank-string))
           (when include-large-text-values? (value-to-map :header             (.getStandardLicenseHeader         lic) nil-blank-string))
           (when include-large-text-values? (value-to-map :header-html        (.getLicenseHeaderHtml             lic) nil-blank-string))
           (when include-large-text-values? (value-to-map :header-template    (.getStandardLicenseHeaderTemplate lic) nil-blank-string)))))

(defn id->license
  "Turns a valid license id into a `org.spdx.library.model.license.SpdxListedLicense`
  object, or returns nil.

  Note: unlike the underlying Java library, this function only handles listed
  SPDX license ids."
  ^org.spdx.library.model.license.SpdxListedLicense [^String id]
  (when (listed-license-id? id) (.getListedLicenseById ^org.spdx.library.model.license.ListedLicenses @is/list-obj id)))

(defn exception->map
  "Turns a `org.spdx.library.model.license.ListedLicenseException` object into a
  map. All map keys are optional, but may include:

  * `:id`                 - `String` (an SPDX identifier)
  * `:name`               - `String`
  * `:comment`            - `String`
  * `:see-also`           - sequence of `String`s
  * `:deprecated?`        - `boolean`
  * `:deprecated-version` - `String`
  * `:text`               - `String`
  * `:text-html`          - `String`
  * `:text-template`      - `String`

  See https://spdx.github.io/Spdx-Java-Library/org/spdx/library/model/license/ListedLicenseException.html
  for more information.

  `opts` are:

  * `:include-large-text-values?` (default `false`) - controls whether the
    following large text values are included in the result: `:comment :text
    :text-html :text-template`"
  ^java.util.Map [^org.spdx.library.model.license.ListedLicenseException exc
                  {:keys [include-large-text-values?] :or {include-large-text-values? false}}]

  (when exc
    (merge (value-to-map :id                 (.getLicenseExceptionId exc))
           (value-to-map :name               (.getName exc))
;           (value-to-map :type               (.getType exc))    ; Spdx-Java-Library implementation detail
           (value-to-map :see-also           (seq (.getSeeAlso exc)))
           (value-to-map :deprecated?        (.isDeprecated exc)                boolean)
           (value-to-map :deprecated-version (.getDeprecatedVersion exc)        nil-blank-string)
           (when include-large-text-values? (value-to-map :comment            (.getComment exc)                  nil-blank-string))
           (when include-large-text-values? (value-to-map :text               (.getLicenseExceptionText exc)     nil-blank-string))
           (when include-large-text-values? (value-to-map :text-html          (.getExceptionTextHtml exc)        nil-blank-string))
           (when include-large-text-values? (value-to-map :text-template      (.getLicenseExceptionTemplate exc) nil-blank-string)))))

(defn id->exception
  "Turns a valid exception id into a org.spdx.library.model.license.ListedLicenseException
  object, or returns nil. Note: unlike the underlying Java library it only
  handles listed SPDX exception ids."
  ^org.spdx.library.model.license.ListedLicenseException [^String id]
  (when (listed-exception-id? id) (.getListedExceptionById ^org.spdx.library.model.license.ListedLicenses @is/list-obj id)))

(defn line-column->map
  "Turns a `org.spdx.utility.compare.LineColumn` object into a map. All map keys
  are optional, but may include:

  * `:line`   - `integer`
  * `:column` - `integer`
  * `:length` - `integer`"
  ^java.util.Map [^org.spdx.utility.compare.LineColumn lc]
  (when lc
    (merge (value-to-map :line   (.getLine   lc))
           (value-to-map :column (.getColumn lc))
           (value-to-map :length (.getLen    lc)))))

(defn difference-description->map
  "Turns a `org.spdx.utility.compare.CompareTemplateOutputHandler$DifferenceDescription`
  object into a map. All map keys are optional, but may include:

  * `:differences-found?`  - `boolean`
  * `:message`             - `String`
  * `:differences`         - sequence of maps (see [[line-column->map]] for
                             details)

  Note: neither `:message` nor `:differences` will be present when
  `:differences-found?` is `false` (unlike the underlying Java library)."
  ^java.util.Map [^org.spdx.utility.compare.CompareTemplateOutputHandler$DifferenceDescription dd]
  (when dd
    (let [difference-found? (.isDifferenceFound dd)]
      (merge {:differences-found? difference-found?}
             (when difference-found?
               (merge (value-to-map :message (.getDifferenceMessage dd))
                      (when-let [differences (seq (map line-column->map (.getDifferences dd)))]
                        {:differences differences})))))))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning nil. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it."
  []
  (is/init!)
  nil)
