(ns eftest.report.output-catcher
  "A test reporter that hides test output for successful tests."
  (:require [clojure.test :as test]
            [eftest.report :refer [*context*]]
            [eftest.report.pretty :as pretty]
            [eftest.report.progress :as progress]
            [io.aviso.ansi :as ansi])
  (:import java.io.StringWriter))

(def ^:dynamic *fonts*
  {:banner ansi/white-font})

(defmulti before-report :type)

(defmethod before-report :default [m])

(defmethod before-report :begin-test-ns [m]
  (swap! *context* assoc ::tests-to-print #{}))

(defn- mark-for-printing []
  (let [test-var (first test/*testing-vars*)]
    (swap! *context* update ::tests-to-print conj test-var)))

(defmethod before-report :error [m]
  (mark-for-printing))

(defmethod before-report :fail [m]
  (mark-for-printing))

(defmethod before-report :end-test-var [m]
  (let [output (str *out*)
        print? (contains? (::tests-to-print @*context*) (:var m))]
    (pop-thread-bindings)
    (when print?
      (test/with-test-out
        (println pretty/*divider*)
        (print (:banner *fonts*))
        (println "==== Test output for" (:var m) "====" (:reset pretty/*fonts*))
        (print output)
        (print (:banner *fonts*))
        (println "==== Test output ends ====" (:reset pretty/*fonts*))))))

(defn after-report [m]
  (when (= (:type m) :begin-test-var)
    (push-thread-bindings {#'*out* (StringWriter.)})))

(defn wrap [f]
  (fn [m]
    (before-report m)
    (f m)
    (after-report m)))

(def report (wrap progress/report))
