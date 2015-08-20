(ns eftest.runner
  (:require [clojure.java.io :as io]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as find]
            [eftest.report :as report]
            [eftest.report.progress :as progress]))

(defn- fixed-inc-report-counter [name]
  (when test/*report-counters*
    (dosync (commute test/*report-counters* update-in [name] (fnil inc 0)))))

;; Backport fix for CLJ-1528 in versions of Clojure pre-1.7
(alter-var-root #'test/inc-report-counter (constantly fixed-inc-report-counter))

(defmethod test/report :begin-test-run [_])

(defn- synchronize [f]
  (let [lock (Object.)] (fn [x] (locking lock (f x)))))

(defn- test-vars [ns vars opts]
  (let [once-fixtures (-> ns meta ::test/once-fixtures test/join-fixtures)
        each-fixtures (-> ns meta ::test/each-fixtures test/join-fixtures)
        report        (synchronize test/report)
        test-var      (fn [v] (binding [test/report report] (test/test-var v)))]
    (once-fixtures
     (fn []
       (if (:multithread? opts true)
         (dorun (pmap (bound-fn [v] (each-fixtures #(test-var v))) vars))
         (doseq [v vars] (each-fixtures #(test-var v))))))))

(defn- test-ns [ns vars opts]
  (let [ns (the-ns ns)]
    (binding [test/*report-counters* (ref test/*initial-report-counters*)]
      (test/do-report {:type :begin-test-ns, :ns ns})
      (test-vars ns vars opts)
      (test/do-report {:type :end-test-ns, :ns ns})
      @test/*report-counters*)))

(defn- test-all [vars opts]
  (->> (group-by (comp :ns meta) vars)
       (map (fn [[ns vars]] (test-ns ns vars opts)))
       (apply merge-with +)))

(defn- require-namespaces-in-dir [dir]
  (map (fn [ns] (require ns) (find-ns ns)) (find/find-namespaces-in-dir (io/file dir))))

(defn- find-tests-in-namespace [ns]
  (->> ns ns-interns vals (filter (comp :test meta))))

(defmulti find-tests type)

(derive clojure.lang.Namespace ::namespace)
(derive clojure.lang.Symbol    ::namespace)
(derive java.io.File           ::directory)
(derive java.lang.String       ::directory)

(defmethod find-tests ::namespace [ns]
  (find-tests-in-namespace ns))

(defmethod find-tests ::directory [dir]
  (mapcat find-tests-in-namespace (require-namespaces-in-dir dir)))

(defn run-tests
  ([tests] (run-tests tests {}))
  ([tests opts]
   (let [start-time (System/currentTimeMillis)
         var-filter (:filter opts (constantly true))
         vars       (filter var-filter (find-tests tests))]
     (binding [report/*context* (atom {})
               test/report      (:report opts progress/report)]
       (test/do-report {:type :begin-test-run, :count (count vars)})
       (let [counters (test-all vars opts)
             duration (- (System/currentTimeMillis) start-time)]
         (test/do-report (assoc counters :type :summary, :duration duration)))))))
