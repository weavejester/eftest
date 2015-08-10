(ns eftest.core
  (:require [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as find]
            [progrock.core :as prog]))

(defn require-namespaces-in-dir [dir]
  (map (fn [ns] (require ns) (find-ns ns)) (find/find-namespaces-in-dir (io/file dir))))

(defn find-tests-in-namespace [ns]
  (->> ns ns-interns vals (filter (comp :test meta))))

(defn find-tests-in-dir [dir]
  (mapcat find-tests-in-namespace (require-namespaces-in-dir dir)))

(defonce clojure-report-methods (methods test/report))

(defmulti clojure-report :type)

(doseq [[type method] clojure-report-methods]
  (defmethod clojure-report type [m] (method m)))

(def ^:dynamic *report* clojure-report)

(doseq [[type method] clojure-report-methods]
  (defmethod test/report type [m] (*report* m)))

(declare ^:dynamic *progress*)

(defmulti progress-report (fn [_ m] (:type m)))

(defmethod progress-report :default [bar m])

(defmethod progress-report :pass [bar m]
  (prog/print (swap! bar prog/tick)))

(defmethod progress-report :fail [bar m]
  (prog/print (swap! bar prog/tick)))

(defmethod progress-report :error [bar m]
  (prog/print (swap! bar prog/tick)))

(defmethod progress-report :summary [bar m]
  (prog/print (swap! bar prog/done)))

(defn wrap-progress-bar [f]
  (fn [vars]
    (let [bar (atom (prog/progress-bar (count vars)))]
      (binding [*report* (partial progress-report bar)]
        (prog/print @bar)
        (f vars)))))

(defn test-dir [dir]
  ((wrap-progress-bar test/test-vars) (find-tests-in-dir dir)))
