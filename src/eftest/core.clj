(ns eftest.core
  (:require [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as find]
            [progrock.core :as prog]
            [io.aviso.ansi :as ansi]))

(defn require-namespaces-in-dir [dir]
  (map (fn [ns] (require ns) (find-ns ns)) (find/find-namespaces-in-dir (io/file dir))))

(defn find-tests-in-namespace [ns]
  (->> ns ns-interns vals (filter (comp :test meta))))

(defn find-tests-in-dir [dir]
  (mapcat find-tests-in-namespace (require-namespaces-in-dir dir)))

(defmethod test/report :begin-test-run [_])
(defmethod test/report :end-test-run [_])

(defn test-vars [vars]
  (test/report {:type :begin-test-run, :vars vars})
  (try (test/test-vars vars)
       (finally (test/report {:type :end-test-run, :vars vars}))))

(defn test-dir [dir]
  (test-vars (find-tests-in-dir dir)))

(defonce clojure-report-methods (methods test/report))

(defmulti clojure-report :type)

(doseq [[type method] clojure-report-methods]
  (defmethod clojure-report type [m] (method m)))

(def ^:dynamic *report* clojure-report)

(doseq [[type method] clojure-report-methods]
  (defmethod test/report type [m] (*report* m)))

(def base-format ":progress/:total   :percent% [:bar]  ETA: :remaining")
(def pass-format (ansi/green base-format))
(def fail-format (ansi/red base-format))

(defmulti report (fn [_ m] (:type m)))

(defmethod report :default [_ m])

(defmethod report :begin-test-run [bar m]
  (prog/print (reset! bar (prog/progress-bar (count (:vars m)))) {:format pass-format}))

(defmethod report :pass [bar m]
  (prog/print (swap! bar prog/tick) {:format pass-format}))

(defmethod report :fail [bar m]
  (prog/print (swap! bar prog/tick) {:format fail-format}))

(defmethod report :error [bar m]
  (prog/print (swap! bar prog/tick) {:format fail-format}))

(defmethod report :summary [bar m]
  (prog/print (swap! bar prog/done)))

(defn run-tests [dir]
  (binding [*report* (partial report (atom nil))]
    (test-dir dir)))
