(ns eftest.core
  (:require [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as find]))

(defmethod test/report :begin-test-run [_])

(defn require-namespaces-in-dir [dir]
  (map (fn [ns] (require ns) (find-ns ns)) (find/find-namespaces-in-dir (io/file dir))))

(defn find-tests-in-namespace [ns]
  (->> ns ns-interns vals (filter (comp :test meta))))

(defn find-tests-in-dir [dir]
  (mapcat find-tests-in-namespace (require-namespaces-in-dir dir)))

(defn test-ns-vars [ns vars]
  (binding [test/*report-counters* (ref test/*initial-report-counters*)]
    (test/do-report {:type :begin-test-ns, :ns ns})
    (test/test-vars vars)
    (test/do-report {:type :end-test-ns, :ns ns})
    @test/*report-counters*))

(defn locking-report [report]
  (let [lock (Object.)]
    (fn [m] (locking lock (report m)))))

(defn test-vars [vars]
  (test/do-report {:type :begin-test-run, :vars vars})
  (let [report   (locking-report test/report)
        counters (pmap (fn [[ns vars]]
                         (binding [test/report report]
                           (test-ns-vars ns vars)))
                       (group-by (comp :ns meta) vars))]
    (test/do-report (-> (apply merge-with + counters)
                        (assoc :type :summary)))))

(defn test-dir [dir]
  (test-vars (find-tests-in-dir dir)))
