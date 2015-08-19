(ns eftest.runner
  (:require [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as find]))

(defn- fixed-inc-report-counter [name]
  (when test/*report-counters*
    (dosync (commute test/*report-counters* update-in [name] (fnil inc 0)))))

;; Backport fix for CLJ-1528 in versions of Clojure pre-1.7
(alter-var-root #'test/inc-report-counter (constantly fixed-inc-report-counter))

(defmethod test/report :begin-test-run [_])

(defn require-namespaces-in-dir [dir]
  (map (fn [ns] (require ns) (find-ns ns)) (find/find-namespaces-in-dir (io/file dir))))

(defn find-tests-in-namespace [ns]
  (->> ns ns-interns vals (filter (comp :test meta))))

(defn find-tests-in-dir [dir]
  (mapcat find-tests-in-namespace (require-namespaces-in-dir dir)))

(defn synchronize [f]
  (let [lock (Object.)] (fn [x] (locking lock (f x)))))

(defn test-vars [vars]
  (doseq [[ns vars] (group-by (comp :ns meta) vars)]
    (let [once-fixtures (-> ns meta ::test/once-fixtures test/join-fixtures)
          each-fixtures (-> ns meta ::test/each-fixtures test/join-fixtures)
          report        (synchronize test/report)
          test-var      (fn [v] (binding [test/report report] (test/test-var v)))]
      (once-fixtures
       (fn [] (dorun (pmap (bound-fn [v] (each-fixtures #(test-var v))) vars)))))))

(defn test-ns
  ([ns] (test-ns ns (constantly true)))
  ([ns pred]
   (let [ns (the-ns ns)]
     (binding [test/*report-counters* (ref test/*initial-report-counters*)]
       (test/do-report {:type :begin-test-ns, :ns ns})
       (if-let [hook (find-var (symbol (str (ns-name ns)) "test-ns-hook"))]
         ((var-get hook))
         (test-vars (filter pred (find-tests-in-namespace ns))))
       (test/do-report {:type :end-test-ns, :ns ns})
       @test/*report-counters*))))

(defn test-dir
  ([dir] (test-dir dir (constantly true)))
  ([dir pred]
   (->> (require-namespaces-in-dir dir)
        (map #(test-ns % pred))
        (apply merge-with +))))

(declare ^:dynamic *context*)

(defn run-tests
  ([dir] (run-tests dir (constantly true)))
  ([dir pred]
   (let [start-time (System/currentTimeMillis)]
     (binding [*context* (atom {})]
       (test/do-report {:type :begin-test-run, :count (count (find-tests-in-dir dir))})
       (let [counters (test-dir dir pred)
             duration (- (System/currentTimeMillis) start-time)]
         (test/do-report (assoc counters :type :summary, :duration duration)))))))
