(ns eftest.report
  (:require [clojure.java.io :as io]
            [clojure.test :refer [*test-out*]]))

(def ^:dynamic *context*
  "Used by eftest.runner/run-tests to hold a mutable atom that persists for the
  duration of the test run. This atom can be used by reporters to hold
  additional statistics and information during the tests."
  nil)

(def ^:dynamic *testing-path*
  "2-element vector [ns scope] where scope is either :clojure.test/once-fixtures,
  :clojure.test/each-fixtures or var under test"
  nil)

(defn combined-reporter
  "Combines the reporters by running first one directly,
  and others with clojure.test/*report-counters* bound to nil."
  [[report & rst]]
  (fn [m]
    (report m)
    (doseq [report rst]
      (binding [clojure.test/*report-counters* nil]
        (report m)))))

(defn report-to-file
  "Wrap a report function so that its output is directed to a file. output-file
  should be something that can be coerced into a Writer."
  [report output-file]
  (let [output-key [::output-files output-file]]
    (fn [m]
      (when (= (:type m) :begin-test-run)
        (io/make-parents (io/file output-file))
        (swap! *context* assoc-in output-key (io/writer output-file)))
      (let [writer (get-in @*context* output-key)]
        (binding [*test-out* writer]
          (report m))
        (when (= (:type m) :summary)
          (swap! *context* update ::output-files dissoc output-file)
          (.close writer))))))
