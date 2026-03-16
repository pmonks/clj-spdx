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
            [rencg.api         :as ncg]
            [spdx.identifiers  :as si]
            [spdx.licenses     :as sl]
            [spdx.exceptions   :as se]
            [spdx.impl.regexes :as sir]))

(defn build-re
  "Returns a regex (`Pattern`) that can find or match any one of the given SPDX
  `ids` (a sequence of `String`s) in a source text. Returns `nil` if `ids` is
  `nil` or empty.

  The regex includes these named capturing groups:

  * `Identifier` (always present) - captures the entire identifier, LicenseRef
    or AdditionRef
  * `DocumentRef` (optional) - captures the `DocumentRef` variable text of a
    LicenseRef, if that's what's matched and it contains one
  * LicenseRef (optional) - captures the LicenseRef variable text of a
    LicenseRef, if that's what's matched
  * `AdditionDocumentRef` (optional) - captures the `DocumentRef` variable text
    of an AdditionRef, if that's what's matched and it contains one
  * AdditionRef (optional) - captures the AdditionRef variable text of an
    AdditionRef, if that's what's matched

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

  * `include-license-refs?` (`boolean`, default `false`) - controls whether
    LicenseRef support is also included in the regex
  * `include-addition-refs?` (`boolean`, default `false`) - controls whether
    AdditionRef support is also included in the regex"
  ([ids] (build-re ids nil))
  ([ids {:keys [include-license-refs?
                include-addition-refs?]
         :or   {include-license-refs?  false
                include-addition-refs? false}}]
   (when (seq ids)
     (let [id-fragments (s/join "|" (map re/esc (sort-by #(* -1 (count %)) ids)))]  ; Sort ids longest to shortest
       (re/join (re/-lb #"\w")
                (re/ncg "Identifier"
                        (when include-license-refs?  (str @sir/license-ref-fragment-re-d "|"))
                        (when include-addition-refs? (str @sir/addition-ref-fragment-re-d "|"))
                        (re/fgrp "i" id-fragments))
                (re/-la #"\w"))))))

(def ^:private ids-re-d (delay (build-re (concat (sl/ids) (se/ids)) {:case-sensitive? false :include-license-refs? true :include-addition-refs? true})))

(defn ids-re
  "Returns a regex (`Pattern`) that can find or match any SPDX license
  identifier, SPDX exception identifier, LicenseRef, or AdditionRef in
  a source text.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @ids-re-d)

(def ^:private license-ids-re-d (delay (build-re (sl/ids) {:case-sensitive? false :include-license-refs? true :include-addition-refs? false})))

(defn license-ids-re
  "Returns a regex (`Pattern`) that can find or match any SPDX license
  identifier, or LicenseRef in a source text.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @license-ids-re-d)

(def ^:private exception-ids-re-d (delay (build-re (se/ids) {:case-sensitive? false :include-license-refs? false :include-addition-refs? true})))

(defn exception-ids-re
  "Returns a regex (`Pattern`) that can find or match any SPDX license exception
  identifier, or AdditionRef in a source text.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @exception-ids-re-d)

(defn license-ref-re
  "Returns a regex (`Pattern`) that can find or match any SPDX LicenseRef.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @sir/license-ref-re-d)

(defn addition-ref-re
 "Returns a regex (`Pattern`) that can find or match any SPDX AdditionRef.

  Specifics of the regex are as for [[build-re]].

  Notes:

  * Caches the generated `Pattern` object and returns it on subsequent calls, so
    is efficient when called many times"
  []
  @sir/addition-ref-re-d)

(defn id-seq-matches
  "Returns a lazy sequence of maps representing each of the identifier matches
  found in `text`, in the order in which they were found, or `nil` if no matches
  were found. `re` must be a regex returned by one of the fns in this namespace,
  and defaults to [[ids-re]] if not provided.

  Each map in the result may contain these keys:

  * `:identifier` (always present) - the canonical represention of the listed
    identifier, LicenseRef or AdditionRef that matched
  * `:type` (always present) - identifier type, as per [[spdx.identifiers/id-type]]
  * `:license-ref` (optional) - the LicenseRef's tag value, if it's a LicenseRef
  * `:document-ref` (optional) - the LicenseRef's DocumentRef tag value, if it's
    a LicenseRef and it has a DocumentRef
  * `:addition-ref` (optional) - the AdditionRef's tag value, if it's an
    AdditionRef
  * `:addition-document-ref` (optional) - the AdditionRef's DocumentRef tag
    value, if it's an AdditionRef and it has a DocumentRef"
  ([^String text] (id-seq-matches @ids-re-d text))
  ([^java.util.regex.Pattern re ^String text]
   (when (and re text)
     (when-let [matches (ncg/re-seq re text)]
       (seq (map #(let [canonical-id-or-ref (si/canonicalise (get % "Identifier"))]
                    (dissoc (merge (assoc % :identifier canonical-id-or-ref
                                            :type       (si/id-type canonical-id-or-ref))
                                   (when-let [document-ref          (get % "DocumentRef")]         {:document-ref          document-ref})
                                   (when-let [license-ref           (get % "LicenseRef")]          {:license-ref           license-ref})
                                   (when-let [addition-document-ref (get % "AdditionDocumentRef")] {:addition-document-ref addition-document-ref})
                                   (when-let [addition-ref          (get % "AdditionRef")]         {:addition-ref          addition-ref}))
                            "Identifier" "DocumentRef" "LicenseRef" "AdditionDocumentRef" "AdditionRef"))
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

  Note: [this function may have a substantial performance cost](https://github.com/pmonks/clj-spdx?tab=readme-ov-file#a-note-about-spdx-license-list-assets)."
  []
  (sl/init!)
  (se/init!)
  (si/init!)
  (sir/init!)
  ; Note: we always lazy-initialise all of the regexes, as it's unlikely that
  ; a caller will use all of them, and they're quick to construct. This saves
  ; callers unecessary memory consumption (an unrealised delay, while not free,
  ; consumes very little memory - 96 bytes on my machine).
  nil)
