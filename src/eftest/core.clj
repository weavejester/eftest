(ns eftest.core
  (:require [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as find]))

(defn test-dir [dir]
  (doseq [ns (find/find-namespaces-in-dir (io/file dir))]
    (require ns)
    (test/test-ns ns)))
