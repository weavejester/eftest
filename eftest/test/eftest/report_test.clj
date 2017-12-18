(ns eftest.report-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [eftest.report.junit :as junit]
            [eftest.runner :as sut]
            [eftest.report :as report]))

(in-ns 'eftest.test-ns.single-failing-test)
(clojure.core/refer-clojure)
(clojure.core/require 'clojure.test)
(clojure.test/deftest single-failing-test
  (clojure.test/is (= 1 2)))

(in-ns 'eftest.report-test)

(defn delete-dir [file]
  (doseq [f (reverse (file-seq file))]
    (.delete f)))

(deftest report-to-file-test
  (delete-dir (io/file "target/test-out"))
  (-> 'eftest.test-ns.single-failing-test
      sut/find-tests
      (sut/run-tests {:report (report/report-to-file junit/report "target/test-out/junit.xml")}))
  (is (string? (slurp "target/test-out/junit.xml"))))
