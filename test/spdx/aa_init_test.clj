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
            [spdx.test-utils]      ; Unused, but we force it to run first
            [spdx.impl.state   :as sis]
            [spdx.impl.mapping :as sim]
            [spdx.licenses     :as sl]
            [spdx.exceptions   :as se]
            [spdx.expressions  :as sexp]))

; clojure.core/time, but with improved output
(defmacro my-time
  "Evaluates expr and prints the time it took.  Returns the value of expr."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     (println (format "%.3f secs" (/ (double (- (. System (nanoTime)) start#)) 1000000000.0)))
     ret#))

; This has to be a single test to ensure ordering
(deftest init!-tests
  (testing "impl ns's init!"
    (is (nil? (sis/init!)))
    (is (nil? (sim/init!))))
  (testing "spdx.licenses/init!"
    (print "spdx.licenses/init! took: ") (flush)
    (is (nil? (my-time (sl/init!))))   ; Note: this call is slow (it can take > 1 minute on my laptop), as it forces full initialisation of the underlying Java library
    (let [elapsed-time (parse-double (re-find #"[\d\.]+" (with-out-str (my-time (sl/init!)))))]   ; Note: this regex isn't quite correct, since it will also match things like 1.2.3. (time) doesn't return messages containing that however.
      (is (< elapsed-time 500.0))))   ; This call should be a LOT less than 0.5 second, on basically any computer
  (testing "spdx.exceptions/init!"
    (print "spdx.exceptions/init! took: ") (flush)
    (is (nil? (my-time (se/init!))))
    (let [elapsed-time (parse-double (re-find #"[\d\.]+" (with-out-str (my-time (se/init!)))))]   ; Note: this regex isn't quite correct, since it will also match things like 1.2.3. (time) doesn't return messages containing that however.
      (is (< elapsed-time 500.0))))   ; This call should be a LOT less than 0.5 second, on basically any computer
  (testing "spdx.expressions/init!"
    (print "spdx.expressions/init! took: ") (flush)
    (is (nil? (my-time (sexp/init!))))
    (let [elapsed-time (parse-double (re-find #"[\d\.]+" (with-out-str (my-time (sexp/init!)))))]   ; Note: this regex isn't quite correct, since it will also match things like 1.2.3. (time) doesn't return messages containing that however.
      (is (< elapsed-time 500.0)))   ; This call should be a LOT less than 0.5 second, on basically any computer
    (println (str "\nℹ️ Using SPDX license list v" (sl/version))) (flush)))
