(ns eftest.report.progress
  (:require [clojure.test :as test]
            [eftest.runner :as runner]
            [eftest.report.pretty :as pretty]
            [progrock.core :as prog]))

(def ^:private clear-line (apply str "\r" (repeat 80 " ")))

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :begin-test-run [m]
  (test/with-test-out
    (prog/print (reset! runner/*context* (prog/progress-bar (:count m))))))

(defmethod report :pass [m]
  (pretty/report m))

(defmethod report :fail [m]
  (test/with-test-out
    (print clear-line)
    (pretty/report m)
    (newline)
    (prog/print @runner/*context*)))

(defmethod report :error [m]
  (test/with-test-out
    (print clear-line)
    (pretty/report m)
    (newline)
    (prog/print @runner/*context*)))

(defmethod report :end-test-var [m]
  (test/with-test-out
    (prog/print (swap! runner/*context* prog/tick))))

(defmethod report :summary [m]
  (test/with-test-out
    (prog/print (swap! runner/*context* prog/done))
    (pretty/report m)))
