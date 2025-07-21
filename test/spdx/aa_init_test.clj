;
; Copyright © 2023 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

; Naming is a hack to get it to run first
(ns spdx.aa-init-test
  (:require [clojure.test      :refer [deftest testing is]]
            [spdx.test-utils]      ; Unused here, but we force it to run first
            [spdx.impl.state   :as sis]
            [spdx.impl.mapping :as sim]
            [spdx.licenses     :as lic]
            [spdx.exceptions   :as exc]
            [spdx.identifiers  :as ids]
            [spdx.expressions  :as exp]
            [spdx.regexes      :as rgx]))

; clojure.core/time, but with improved output
(defmacro my-time
  "Evaluates expr and prints the time it took.  Returns the value of expr."
  [expr]
  `(let [start# (. System (nanoTime))
         ret#   ~expr]
     (println (format "%.3f secs" (/ (double (- (. System (nanoTime)) start#)) 1000000000.0)))
     ret#))

(defmacro elapsed-time
  "Evaluates expr and returns the time it took. Value of expr is thrown away."
  [expr]
  `(let [start# (. System (nanoTime))]
     ~expr
     (/ (double (- (. System (nanoTime)) start#)) 1000000000.0)))

; This has to be a single test to ensure ordering
(deftest init!-tests
  (testing "impl ns's init!"
    (is (nil? (sis/init!)))
    (is (nil? (sim/init!))))
  (testing "spdx.licenses/init!"
    (print "spdx.licenses/init! took: ") (flush)
    (is (nil? (my-time (lic/init!))))           ; This first call is slow (it can take > 1 minute on my laptop), as it forces initialisation of some of the underlying Java library
    (is (< (elapsed-time (lic/init!)) 500.0)))  ; This second call should be a LOT less than 0.5 second, on basically any computer
  (testing "spdx.exceptions/init!"
    (print "spdx.exceptions/init! took: ") (flush)
    (is (nil? (my-time (exc/init!))))           ; This first call is slow (albeit nowhere near as slow as lic/init), as it forces initialisation of some of the underlying Java library
    (is (< (elapsed-time (exc/init!)) 500.0)))  ; This second call should be a LOT less than 0.5 second, on basically any computer
  (testing "spdx.identifiers/init!"
    (print "spdx.identifiers/init! took: ") (flush)
    (is (nil? (my-time (ids/init!))))
    (is (< (elapsed-time (ids/init!)) 500.0)))  ; This second call should be a LOT less than 0.5 second, on basically any computer
  (testing "spdx.expressions/init!"
    (print "spdx.expressions/init! took: ") (flush)
    (is (nil? (my-time (exp/init!))))
    (is (< (elapsed-time (exp/init!)) 500.0)))  ; This second call should be a LOT less than 0.5 second, on basically any computer
  (testing "spdx.regexes/init!"
    (print "spdx.regexes/init! took: ") (flush)
    (is (nil? (my-time (rgx/init!))))
    (is (< (elapsed-time (rgx/init!)) 500.0)))  ; This second call should be a LOT less than 0.5 second, on basically any computer
  (println (str "\nℹ️ Using SPDX license list v" (ids/version))) (flush))
