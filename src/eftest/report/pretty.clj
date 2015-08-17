(ns eftest.report.pretty
  (:require [clojure.test :as test]
            [io.aviso.ansi :as ansi]))

(def test-report test/report)

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :pass [m] (test-report m))

(defmethod report :fail [m] (test-report m))

(defmethod report :error [m] (test-report m))

(defmethod report :summary [m] (test-report m))
