(ns eftest.report.progress
  (:require [clojure.test :as test]
            [eftest.runner :as runner]
            [eftest.report.pretty :as pretty]
            [progrock.core :as prog]))

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :begin-test-run [m]
  (test/with-test-out
    (prog/print (reset! runner/*context* (prog/progress-bar (:count m))))))

(defmethod report :end-test-var [m]
  (test/with-test-out
    (prog/print (swap! runner/*context* prog/tick))))

(defmethod report :summary [m]
  (test/with-test-out
    (prog/print (swap! runner/*context* prog/done))
    (pretty/report m)))
