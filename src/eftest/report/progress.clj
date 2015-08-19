(ns eftest.report.progress
  (:require [clojure.test :as test]
            [eftest.runner :as runner]
            [eftest.report.pretty :as pretty]
            [progrock.core :as prog]))

(def ^:private clear-line (apply str "\r" (repeat 80 " ")))

(defn print-progress [context]
  (prog/print (:bar context)))

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :begin-test-run [m]
  (test/with-test-out
    (print-progress (reset! runner/*context* {:bar (prog/progress-bar (:count m))}))))

(defmethod report :pass [m]
  (pretty/report m))

(defmethod report :fail [m]
  (test/with-test-out
    (print clear-line)
    (pretty/report m)
    (newline)
    (print-progress @runner/*context*)))

(defmethod report :error [m]
  (test/with-test-out
    (print clear-line)
    (pretty/report m)
    (newline)
    (print-progress @runner/*context*)))

(defmethod report :end-test-var [m]
  (test/with-test-out
    (print-progress (swap! runner/*context* update-in [:bar] prog/tick))))

(defmethod report :summary [m]
  (test/with-test-out
    (print-progress (swap! runner/*context* update-in [:bar] prog/done))
    (pretty/report m)))
