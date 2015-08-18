(ns eftest.report.pretty
  (:require [clojure.test :as test]
            [io.aviso.ansi :as ansi]))

(def test-report test/report)

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :pass [m] (test-report m))

(defmethod report :fail [m] (test-report m))

(defmethod report :error [m] (test-report m))

(defmethod report :summary [{:keys [test pass fail error]}]
  (let [total (+ pass fail error)
        color (if (= pass total) ansi/green ansi/red)]
    (test/with-test-out
      (newline)
      (println "Ran" test "tests containing" total "assertions.")
      (println (color (str fail " failures, " error " errors."))))))
