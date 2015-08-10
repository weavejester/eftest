(ns eftest.core
  (:require [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as find]))

(defn require-namespaces-in-dir [dir]
  (map (fn [ns] (require ns) (find-ns ns)) (find/find-namespaces-in-dir (io/file dir))))

(defn find-tests-in-namespace [ns]
  (->> ns ns-interns vals (filter (comp :test meta))))

(defn find-tests-in-dir [dir]
  (mapcat find-tests-in-namespace (require-namespaces-in-dir dir)))

(defn test-dir [dir]
  (test/test-vars (find-tests-in-dir dir)))

(defonce clojure-report-methods (methods test/report))

(defmulti clojure-report :type)

(doseq [[type method] clojure-report-methods]
  (defmethod clojure-report type [m] (method m)))

(def ^:dynamic *report* clojure-report)

(doseq [[type method] clojure-report-methods]
  (defmethod test/report type [m] (*report* m)))
