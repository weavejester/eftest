(ns eftest.runner-test
  (:require [clojure.test :refer :all]
            [eftest.runner :as sut]))

(def tests-completed
  (atom []))

;; Example test namespaces have a prefix such as ns-0 so that they can be
;; deterministically sorted.

(in-ns 'eftest.test-ns-0.throwing-test)
(clojure.core/refer-clojure)
(clojure.core/require 'clojure.test)
(clojure.test/deftest throwing-test
  (swap! eftest.runner-test/tests-completed conj :throwing-test)
  (throw (ex-info "." {})))

(in-ns 'eftest.test-ns-1.throwing-test-in-fixtures)
(clojure.core/refer-clojure)
(clojure.core/require 'clojure.test)
(clojure.test/use-fixtures :once
  (fn [t]
    (swap! eftest.runner-test/tests-completed conj :throwing-test-in-fixtures)
    (throw (ex-info "." {}))
    (t)))
(clojure.test/deftest example-test
  (clojure.test/is (= 1 1)))

(in-ns 'eftest.test-ns-2.single-failing-test)
(clojure.core/refer-clojure)
(clojure.core/require 'clojure.test)
(clojure.test/deftest single-failing-test
  (swap! eftest.runner-test/tests-completed conj :single-failing-test)
  (clojure.test/is (= 1 2)))

(in-ns 'eftest.test-ns-3.another-failing-test)
(clojure.core/refer-clojure)
(clojure.core/require 'clojure.test)
(clojure.test/deftest another-failing-test
  (swap! eftest.runner-test/tests-completed conj :another-failing-test)
  (clojure.test/is (= 3 4)))

(in-ns 'eftest.test-ns.slow-test)
(clojure.core/refer-clojure)
(clojure.core/require 'clojure.test)
(clojure.test/deftest a-slow-test
  (clojure.test/is (true? (do (Thread/sleep 10) true))))

(in-ns 'eftest.runner-test)

(defn with-test-out-str* [f]
  (let [s (java.io.StringWriter.)]
    (binding [clojure.test/*test-out* s]
      (f))
    (-> (str s)
        (.replace "\u001B" "")
        (.replaceAll "\\[([0-9]{1};)?[0-9]{0,2}m" ""))))

(defmacro with-test-out-str [& body]
  `(with-test-out-str* (fn [] ~@body)))

(defn test-run-tests
  ([test-locs]
   (test-run-tests test-locs {}))
  ([test-locs opts]
   (let [vars (sut/find-tests test-locs)
         ret  (promise)
         out  (with-test-out-str (deliver ret (sut/run-tests vars opts)))]
     {:output out
      :return @ret})))

(deftest test-reporting
  (let [out (:output (test-run-tests 'eftest.test-ns-2.single-failing-test))]
    (is (re-find #"FAIL in eftest.test-ns-2.single-failing-test/single-failing-test" out))
    (is (not (re-find #"IllegalArgumentException" out)))))

(deftest test-fail-fast
  (let [run! (fn [nses opts]
               (reset! tests-completed [])
               (-> nses
                   (test-run-tests (merge {:multithread? false} opts))
                   :return
                   (select-keys [:test :fail :error])))]

    (testing "Failing tests"
      (let [result (run! '[eftest.test-ns-2.single-failing-test
                           eftest.test-ns-3.another-failing-test]
                         {:fail-fast? true})]
        (is (= {:test 1 :fail 1 :error 0} result))
        (is (= [:single-failing-test]
               @tests-completed)))

      (let [result (run! '[eftest.test-ns-2.single-failing-test
                           eftest.test-ns-3.another-failing-test]
                         {:fail-fast? false})]
        (is (= {:test 2 :fail 2 :error 0} result))
        (is (= [:single-failing-test :another-failing-test]
               @tests-completed))))

    (testing "Exceptions in tests"
      (let [result (run! '[eftest.test-ns-0.throwing-test
                           eftest.test-ns-2.single-failing-test]
                         {:fail-fast? true})]
        (is (= {:test 1 :fail 0 :error 1} result))
        (is (= [:throwing-test]
               @tests-completed)))

      (let [result (run! '[eftest.test-ns-0.throwing-test
                           eftest.test-ns-2.single-failing-test]
                         {:fail-fast? false})]
        (is (= {:test 2 :fail 1 :error 1} result))
        (is (= [:throwing-test
                :single-failing-test]
               @tests-completed))))

    (testing "Exceptions in fixtures"
      (let [result (run! '[eftest.test-ns-1.throwing-test-in-fixtures
                           eftest.test-ns-2.single-failing-test]
                         {:fail-fast? true})]
        (is (= {:test 0 :fail 0 :error 1} result))
        (is (= [:throwing-test-in-fixtures]
               @tests-completed)))

      (let [result (run! '[eftest.test-ns-1.throwing-test-in-fixtures
                           eftest.test-ns-2.single-failing-test]
                         {:fail-fast? false})]
        (is (= {:test 1 :fail 1 :error 1} result))
        (is (= [:throwing-test-in-fixtures
                :single-failing-test]
               @tests-completed))))))

(deftest test-fail-multi
  (let [out (:output
             (test-run-tests
              '[eftest.test-ns-2.single-failing-test
                eftest.test-ns-3.another-failing-test]))]
    (println out)
    (is (re-find #"(?m)expected: 1\n  actual: 2" out))
    (is (re-find #"(?m)expected: 3\n  actual: 4" out))))

(deftest test-slow-test-report
  (testing "should fail with an accurate var location"
    (let [out (:output
                (test-run-tests ['eftest.test-ns.slow-test] {:test-warn-time 5}))]
      (is (re-find #"LONG TEST in eftest.test-ns.slow-test/a-slow-test\n" out)))))
