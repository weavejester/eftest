(ns eftest.report-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [eftest.report.junit :as junit]
            [eftest.runner :as sut]
            [puget.printer :as puget]
            [eftest.report.pretty :as pretty]
            [eftest.output-capture :as output-capture]
            [eftest.report :as report]))

(in-ns 'eftest.test-ns.single-failing-test)
(clojure.core/refer-clojure)
(clojure.core/require 'clojure.test)
(clojure.test/deftest single-failing-test
  (clojure.test/is (= 1 2)))

(in-ns 'eftest.report-test)

(def ^:private pprint-str #(puget/pprint-str % {:print-color true, :print-meta false}))

(defn delete-dir [file]
  (doseq [f (reverse (file-seq file))]
    (.delete f)))

(deftest report-to-file-test
  (delete-dir (io/file "target/test-out"))
  (-> 'eftest.test-ns.single-failing-test
      sut/find-tests
      (sut/run-tests {:report (report/report-to-file junit/report "target/test-out/junit.xml")}))
  (is (string? (slurp "target/test-out/junit.xml"))))

(def this-ns *ns*)

(deftest file-and-line-in-pretty-fail-report
  (let [pretty-nil (puget/pprint-str nil {:print-color true
                                          :print-meta false})
        result (with-out-str
                (binding [*test-out* *out*
                          pretty/*fonts* {}
                          report/*testing-path* [this-ns #'file-and-line-in-pretty-fail-report]
                          *report-counters* (ref *initial-report-counters*)]
                  (output-capture/with-test-buffer
                    (pretty/report {:type :fail
                                    :file "report_test.clj"
                                    :line 999
                                    :message "foo"}))))]
    (is (= (str "\nFAIL in eftest.report-test/file-and-line-in-pretty-fail-report"
                " (report_test.clj:999)\n"
                "foo\n"
                "expected: "
                pretty-nil
                "\n  actual: "
                pretty-nil
                "\n")
           result))))

(deftest report-fail-with-diff
  (let [result (with-out-str
                (binding [*test-out* *out*
                          pretty/*fonts* {}
                          report/*testing-path* [this-ns #'file-and-line-in-pretty-fail-report]
                          *report-counters* (ref *initial-report-counters*)]
                  (output-capture/with-test-buffer
                    (pretty/report
                     {:file "report_test.clj"
                      :line 999
                      :type :fail
                      :diffs [[{:b 1, :c 2}
                               [{:a 0} {:c 2}]]]
                      :expected {:a 0, :b 1}
                      :actual [{:b 1, :c 2}]
                      :message nil}))))]
    (is (= (str "\nFAIL in eftest.report-test/file-and-line-in-pretty-fail-report"
                " (report_test.clj:999)\n"
                "expected: " (pprint-str {:a 0, :b 1}) "\n"
                "  actual: " (pprint-str {:b 1, :c 2}) "\n"
                "    diff: - " (pprint-str {:a 0}) "\n"
                "          + " (pprint-str{:c 2}) "\n")
           result))))

(deftest report-fail-with-predicate
  (let [result (with-out-str
                (binding [*test-out* *out*
                          pretty/*fonts* {}
                          report/*testing-path* [this-ns #'file-and-line-in-pretty-fail-report]
                          *report-counters* (ref *initial-report-counters*)]
                  (output-capture/with-test-buffer
                    (pretty/report
                     {:file "report_test.clj"
                      :line 999
                      :type :fail
                      :expected '(nil? {})
                      :actual false
                      :message nil
                      :with-values (nil? {})}))))]
    (is (= (str "\nFAIL in eftest.report-test/file-and-line-in-pretty-fail-report"
                " (report_test.clj:999)\n"
                "expected: " (pprint-str '(nil? {})) "\n"
                "  actual: " (pprint-str false) "\n")
           result))))
