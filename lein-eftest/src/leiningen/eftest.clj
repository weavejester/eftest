(ns leiningen.eftest
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.test :as test]
            [leiningen.core.project :as project]))

(def eftest-profile
  {:dependencies '[[eftest "0.3.1"]]})

(defn- report-namespace [project]
  (-> project :eftest (:report 'eftest.report.progress/report) namespace symbol))

(defn- require-form [project]
  `(require 'eftest.runner '~(report-namespace project)))

;; The following three forms are copied from leiningen.test

(def ^:private form-for-suppressing-unselected-tests
  `(fn [namespaces# selectors# func#]
     (let [copy-meta# (fn [var# from-key# to-key#]
                        (if-let [x# (get (meta var#) from-key#)]
                          (alter-meta! var# #(-> % (assoc to-key# x#) (dissoc from-key#)))))
           vars#      (if (seq selectors#)
                        (->> namespaces#
                             (mapcat (comp vals ns-interns))
                             (remove (fn [var#]
                                       (some (fn [[selector# args#]]
                                               (let [sfn# (if (vector? selector#)
                                                            (second selector#)
                                                            selector#)]
                                                 (apply sfn#
                                                        (merge (-> var# meta :ns meta)
                                                               (assoc (meta var#) ::var var#))
                                                        args#)))
                                             selectors#)))))
           copy#      #(doseq [v# vars#] (copy-meta# v# %1 %2))]
       (copy# :test :leiningen/skipped-test)
       (try (func#)
            (finally
              (copy# :leiningen/skipped-test :test))))))

(defn- form-for-select-namespaces [namespaces selectors]
  `(reduce (fn [acc# [f# args#]]
             (if (vector? f#)
               (filter #(apply (first f#) % args#) acc#)
               acc#))
           '~namespaces ~selectors))

(defn- form-for-nses-selectors-match [selectors ns-sym]
  `(distinct
    (for [ns#       ~ns-sym
          [_# var#] (ns-publics ns#)
          :when     (some (fn [[selector# args#]]
                            (apply (if (vector? selector#)
                                     (second selector#)
                                     selector#)
                                   (merge (-> var# meta :ns meta)
                                          (assoc (meta var#) ::var var#))
                                   args#))
                          ~selectors)]
      ns#)))

(defn- testing-form [project namespaces selectors]
  (let [selectors (vec selectors)
        ns-sym    (gensym "namespaces")
        options   (:eftest project {})]
    `(let [~ns-sym              ~(form-for-select-namespaces namespaces selectors)
           _#                   (when (seq ~ns-sym) (apply require :reload ~ns-sym))
           selected-namespaces# ~(form-for-nses-selectors-match selectors ns-sym)
           summary#             (~form-for-suppressing-unselected-tests
                                 selected-namespaces# ~selectors
                                 #(eftest.runner/run-tests
                                   (eftest.runner/find-tests selected-namespaces#)
                                   ~options))
           exit-code#           (+ (:error summary#) (:fail summary#))]
       (if ~(= :leiningen (:eval-in project))
         exit-code#
         (System/exit exit-code#)))))

(defn eftest
  "Run the project's tests with Eftest."
  [project & tests]
  (let [[nses selectors] (test/read-args tests project)
        profiles         [:leiningen/test :test eftest-profile]
        project          (project/merge-profiles project profiles)
        form             (testing-form project nses selectors)]
    (try
      (when-let [n (eval/eval-in-project project form (require-form project))]
        (when (and (number? n) (pos? n))
          (throw (ex-info "Tests Failed" {:exit-code n}))))
      (catch clojure.lang.ExceptionInfo _
        (main/abort "Tests failed.")))))
