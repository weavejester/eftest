(ns eftest.report-test
  (:require
   [clojure.java.shell :as sh :refer [sh]]
   [clojure.test :refer [deftest is]]))

(deftest ^:integration test-junit-reporter
  (sh/with-sh-dir "example"
    (let [{:keys [exit out]} (sh "lein" "with-profile" "+junit" "eftest")]
      (is (< 0 exit))
      (is (= "" out)))))
