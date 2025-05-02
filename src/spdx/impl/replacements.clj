;
; Copyright © 2024 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns spdx.impl.replacements
  "Functionality related to replacing certain ids with equivalents. Note: this
  namespace is not part of the public API of clj-spdx and may change without
  notice.")

; Note: last cross referenced against license list v3.25.0

(def ^:private mandatory-license-id-replacements-d (delay {
  "AGPL-1.0-only+"     "AGPL-1.0-or-later"
  "AGPL-1.0-or-later+" "AGPL-1.0-or-later"
  "AGPL-3.0-only+"     "AGPL-3.0-or-later"
  "AGPL-3.0-or-later+" "AGPL-3.0-or-later"
  "GPL-1.0-only+"      "GPL-1.0-or-later"
  "GPL-1.0-or-later+"  "GPL-1.0-or-later"
  "GPL-2.0-only+"      "GPL-2.0-or-later"
  "GPL-2.0-or-later+"  "GPL-2.0-or-later"
  "GPL-3.0-only+"      "GPL-3.0-or-later"
  "GPL-3.0-or-later+"  "GPL-3.0-or-later"
  "LGPL-2.0-only+"     "LGPL-2.0-or-later"
  "LGPL-2.0-or-later+" "LGPL-2.0-or-later"
  "LGPL-2.1-only+"     "LGPL-2.1-or-later"
  "LGPL-2.1-or-later+" "LGPL-2.1-or-later"
  "LGPL-3.0-only+"     "LGPL-3.0-or-later"
  "LGPL-3.0-or-later+" "LGPL-3.0-or-later"
  "GFDL-1.1-only+"     "GFDL-1.1-or-later"
  "GFDL-1.1-or-later+" "GFDL-1.1-or-later"
  "GFDL-1.2-only+"     "GFDL-1.2-or-later"
  "GFDL-1.2-or-later+" "GFDL-1.2-or-later"
  "GFDL-1.3-only+"     "GFDL-1.3-or-later"
  "GFDL-1.3-or-later+" "GFDL-1.3-or-later"}))

(defn replacement-for-license-id
  "Returns a map representing the replacement of `license-id` & `or-later?`, if
  any.  Returns `nil` if `license-id` is `nil`, or if there is no replacement.

  The map may contain any combination of these keys:

  * `:license-id` - a replacement SPDX license id
  * `:or-later?` - the or-later? flag, which may not be the same as the value
    passed in, depending on what's found

  Notes:

  * `license-id` _must_ be correctly cased as per the SPDX license list"
  [^String license-id or-later?]
  (when license-id
    ; Try looking up with or-later? flag
    (if-let [new-license-id (get @mandatory-license-id-replacements-d (str license-id (when or-later? "+")))]
      {:license-id new-license-id}
      ; Then try looking up without or-later? flag
      (when-let [new-license-id (get @mandatory-license-id-replacements-d license-id)]
        (merge {:license-id new-license-id}
               (when or-later? {:or-later? true}))))))  ; Make sure we preserve the or-later? flag in this case

(def ^:private deprecated-license-id-replacements-d (delay {
  "AGPL-1.0"                          {:license-ids ["AGPL-1.0-only"]}
  "AGPL-1.0+"                         {:license-ids ["AGPL-1.0-or-later"]}    ; Note: AGPL-1.0+ never existed as a listed SPDX license identifier, but we still (optionally) replace it
  "AGPL-3.0"                          {:license-ids ["AGPL-3.0-only"]}
  "AGPL-3.0+"                         {:license-ids ["AGPL-3.0-or-later"]}    ; Note: AGPL-3.0+ never existed as a listed SPDX license identifier, but we still (optionally) replace it
  "GPL-1.0"                           {:license-ids ["GPL-1.0-only"]}
  "GPL-1.0+"                          {:license-ids ["GPL-1.0-or-later"]}
  "GPL-2.0"                           {:license-ids ["GPL-2.0-only"]}
  "GPL-2.0+"                          {:license-ids ["GPL-2.0-or-later"]}
  "GPL-3.0"                           {:license-ids ["GPL-3.0-only"]}
  "GPL-3.0+"                          {:license-ids ["GPL-3.0-or-later"]}
  "LGPL-2.0"                          {:license-ids ["LGPL-2.0-only"]}
  "LGPL-2.0+"                         {:license-ids ["LGPL-2.0-or-later"]}
  "LGPL-2.1"                          {:license-ids ["LGPL-2.1-only"]}
  "LGPL-2.1+"                         {:license-ids ["LGPL-2.1-or-later"]}
  "LGPL-3.0"                          {:license-ids ["LGPL-3.0-only"]}
  "LGPL-3.0+"                         {:license-ids ["LGPL-3.0-or-later"]}
  "GFDL-1.1"                          {:license-ids ["GFDL-1.1-only"]}
  "GFDL-1.1+"                         {:license-ids ["GFDL-1.1-or-later"]}    ; Note: GFDL-1.1+ never existed as a listed SPDX license identifier, but we still (optionally) replace it
  "GFDL-1.2"                          {:license-ids ["GFDL-1.2-only"]}
  "GFDL-1.2+"                         {:license-ids ["GFDL-1.2-or-later"]}    ; Note: GFDL-1.2+ never existed as a listed SPDX license identifier, but we still (optionally) replace it
  "GFDL-1.3"                          {:license-ids ["GFDL-1.3-only"]}
  "GFDL-1.3+"                         {:license-ids ["GFDL-1.3-or-later"]}    ; Note: GFDL-1.3+ never existed as a listed SPDX license identifier, but we still (optionally) replace it
  "GPL-2.0-with-autoconf-exception"   {:license-ids ["GPL-2.0-only"]     :license-exception-id "Autoconf-exception-2.0"}
  "GPL-2.0-with-autoconf-exception+"  {:license-ids ["GPL-2.0-or-later"] :license-exception-id "Autoconf-exception-2.0"}
  "GPL-2.0-with-bison-exception"      {:license-ids ["GPL-2.0-only"]     :license-exception-id "Bison-exception-2.2"}
  "GPL-2.0-with-bison-exception+"     {:license-ids ["GPL-2.0-or-later"] :license-exception-id "Bison-exception-2.2"}
  "GPL-2.0-with-classpath-exception"  {:license-ids ["GPL-2.0-only"]     :license-exception-id "Classpath-exception-2.0"}
  "GPL-2.0-with-classpath-exception+" {:license-ids ["GPL-2.0-or-later"] :license-exception-id "Classpath-exception-2.0"}
  "GPL-2.0-with-font-exception"       {:license-ids ["GPL-2.0-only"]     :license-exception-id "Font-exception-2.0"}
  "GPL-2.0-with-font-exception+"      {:license-ids ["GPL-2.0-or-later"] :license-exception-id "Font-exception-2.0"}
  "GPL-2.0-with-GCC-exception"        {:license-ids ["GPL-2.0-only"]     :license-exception-id "GCC-exception-2.0"}
  "GPL-2.0-with-GCC-exception+"       {:license-ids ["GPL-2.0-or-later"] :license-exception-id "GCC-exception-2.0"}
  "GPL-3.0-with-autoconf-exception"   {:license-ids ["GPL-3.0-only"]     :license-exception-id "Autoconf-exception-3.0"}
  "GPL-3.0-with-autoconf-exception+"  {:license-ids ["GPL-3.0-or-later"] :license-exception-id "Autoconf-exception-3.0"}
  "GPL-3.0-with-GCC-exception"        {:license-ids ["GPL-3.0-only"]     :license-exception-id "GCC-exception-3.1"}
  "GPL-3.0-with-GCC-exception+"       {:license-ids ["GPL-3.0-or-later"] :license-exception-id "GCC-exception-3.1"}
  "StandardML-NJ"                     {:license-ids ["SMLNJ"]}
  "BSD-2-Clause-FreeBSD"              {:license-ids ["BSD-2-Clause-Views"]}
  "BSD-2-Clause-NetBSD"               {:license-ids ["BSD-2-Clause"]}
  "bzip2-1.0.5"                       {:license-ids ["bzip2-1.0.6"]}
  "Net-SNMP"                          {:license-ids ["BSD-3-Clause" "MIT-CMU"]}                                               ; Grab bag of multiple licenses
  "eCos-2.0"                          {:license-ids ["GPL-2.0-only"]     :license-exception-id "eCos-exception-2.0"}          ; Changed from license to GPL-2.0 exception
  "eCos-2.0+"                         {:license-ids ["GPL-2.0-or-later"] :license-exception-id "eCos-exception-2.0"}          ; Changed from license to GPL-2.0 exception
  "wxWindows"                         {:license-ids ["GPL-2.0-only"]     :license-exception-id "WxWindows-exception-3.1"}     ; Changed from license to GPL-2.0 exception
  "wxWindows+"                        {:license-ids ["GPL-2.0-or-later"] :license-exception-id "WxWindows-exception-3.1"}}))  ; Changed from license to GPL-2.0 exception

(defn replacement-for-deprecated-license-id
  "Returns a map representing the replacement of `license-id` & `or-later?`, if
  any.  Returns `nil` if `license-id` is `nil`, or if there is no replacement
  (including the case where it's not deprecated).

  The map may contain any combination of these keys:

  * `:license-ids` - a sequence of replacement SPDX license ids
  * `:license-exception-id` - a single replacement SPDX license
     exception id
  * `:or-later?` - the or-later? flag, which may not be the same as the value
    passed in, depending on what's found

  Notes:

  * `license-id` _must_ be correctly cased as per the SPDX license list
  * Some deprecated SPDX license ids have been replaced with multiple
    license ids - in these cases this should be considered an `AND`
  * Not all deprecated `license-id`s have a replacement.
  * if or-later? is true, the or-later variant is first looked up, and then, if
    no result is found, the 'naked' identifier is also looked up"
  [^String license-id or-later?]
  (when license-id
    (if or-later?
      ; First try looking up with or-later? flag
      (if-let [result (get @deprecated-license-id-replacements-d (str license-id "+"))]
        result
        ; Then try looking up without or-later? flag
        (when-let [result (get @deprecated-license-id-replacements-d license-id)]
          (assoc result :or-later? true)))  ; Make sure we preserve the or-later? flag in this case
      ; or-later? flag was false - look up without it
      (get @deprecated-license-id-replacements-d license-id))))

(def ^:private deprecated-exception-id-replacements-d (delay {
  "Nokia-Qt-exception-1.1" "Qt-LGPL-exception-1.1"}))

(defn replacement-for-deprecated-exception-id
  "Returns the SPDX exception id that supercedes `exception-id`, if any.
  Returns `nil` if `exception-id` was `nil`, or if there is no replacement for
  `exception-id` (including the case where it's not deprecated).

  Notes:

  * `exception-id` _must_ be correctly cased as per the SPDX license exception
    list
  * Not all deprecated `exception-id`s have a replacement."
  [^String exception-id]
  (when exception-id
    (get @deprecated-exception-id-replacements-d exception-id)))

(defn init!
  "Initialises this namespace upon first call (and does nothing on subsequent
  calls), returning `nil`. Consumers of this namespace are not required to call
  this fn, as initialisation will occur implicitly anyway; it is provided to
  allow explicit control of the cost of initialisation to callers who need it."
  []
  @mandatory-license-id-replacements-d
  @deprecated-license-id-replacements-d
  @deprecated-exception-id-replacements-d
  nil)
