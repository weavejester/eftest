(ns leiningen.eftest
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.core.project :as project]))

(def eftest-profile
  {:dependencies '[[eftest "0.2.0"]]})

(defn- testing-form [project]
  (let [paths (vec (:test-paths project))]
    `(let [summary#   (eftest.runner/run-tests (eftest.runner/find-tests ~paths))
           exit-code# (+ (:error summary#) (:fail summary#))]
       (if ~(= :leiningen (:eval-in project))
         exit-code#
         (System/exit exit-code#)))))

(defn eftest
  "Run the project's tests with Eftest."
  [project]
  (let [project (project/merge-profiles project [:leiningen/test :test eftest-profile])
        form    (testing-form project)]
    (try
      (when-let [n (eval/eval-in-project project form `(require 'eftest.runner))]
        (when (and (number? n) (pos? n))
          (throw (ex-info "Tests Failed" {:exit-code n}))))
      (catch clojure.lang.ExceptionInfo _
        (main/abort "Tests failed.")))))
