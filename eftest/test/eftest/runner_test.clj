(ns eftest.runner-test
  (:require [clojure.test :refer :all]
            [eftest.runner :as sut]))

(in-ns 'eftest.test-ns.single-failing-test)
(clojure.core/refer-clojure)
(clojure.core/require 'clojure.test)
(clojure.test/deftest single-failing-test
  (clojure.test/is (= 1 2)))

(in-ns 'eftest.test-ns.another-failing-test)
(clojure.core/refer-clojure)
(clojure.core/require 'clojure.test)
(clojure.test/deftest another-failing-test
  (clojure.test/is (= 1 2)))

(in-ns 'eftest.runner-test)

(defn with-test-out-str* [f]
  (let [s (java.io.StringWriter.)]
    (binding [clojure.test/*test-out* s]
      (f))
    (-> (str s)
        (.replaceAll "\\p{Cntrl}" "")
        (.replaceAll "\\[([0-9]{1};)?[0-9]{0,2}m" ""))))

(defmacro with-test-out-str [& body]
  `(with-test-out-str*
     (fn [] ~@body)))

(defn test-ns-out-str [ns-sym]
  (with-test-out-str
    (-> ns-sym sut/find-tests sut/run-tests)))

(deftest test-reporting
  (let [result (test-ns-out-str 'eftest.test-ns.single-failing-test)]
    (is (re-find #"FAIL in eftest.test-ns.single-failing-test/single-failing-test" result))
    (is (not (re-find #"IllegalArgumentException" result)))))

(deftest test-fail-fast
  (is (= {:test 1 :fail 1}
         (-> (binding [clojure.test/*test-out* (java.io.StringWriter.)]
               (sut/run-tests (concat (sut/find-tests 'eftest.test-ns.single-failing-test)
                                      (sut/find-tests 'eftest.test-ns.another-failing-test))
                              {:fail-fast? true
                               :multithread? false
                               :multithread-ns? false}))
             (select-keys [:test :fail])))))
