(ns leiningen.eftest
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [leiningen.test :as test]
            [leiningen.core.project :as project]))

(def eftest-profile
  {:dependencies '[[eftest "0.4.3"]]})

(defn- report-namespace [project]
  (if-let [reporter (get-in project [:eftest :report])]
    (try
      (-> reporter namespace symbol)
      (catch NullPointerException cause
        (throw (ex-info (str "eftest :report must be a fully-qualified reporter "
                             "function!")
                        {:reporter reporter}
                        cause))))
    'eftest.report.progress))

(defn- require-form [project]
  `(require 'eftest.report 'eftest.runner '~(report-namespace project)))

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

(defn- process-options
  [{:keys [report report-to-file] :as options}]
  ;; Use `or` as opposed to relying on the keyword's IFn to ensure we override
  ;; an explicit/accidental `nil`.
  ;;
  ;; Note, that we've duplicated the default reporter decision both here and in
  ;; `eftest.runner/run-tests`.
  `(let [reporter# (or ~report eftest.report.progress/report)]
     (cond-> (or ~options {})
       (some? ~report-to-file)
       (update :report eftest.report/report-to-file ~report-to-file))))

(defn- testing-form [project namespaces selectors]
  (let [selectors (vec selectors)
        ns-sym    (gensym "namespaces")]
    `(let [~ns-sym              ~(form-for-select-namespaces namespaces selectors)
           _#                   (when (seq ~ns-sym) (apply require :reload ~ns-sym))
           selected-namespaces# ~(form-for-nses-selectors-match selectors ns-sym)
           options#             ~(-> project :eftest process-options)
           summary#             (~form-for-suppressing-unselected-tests
                                 selected-namespaces# ~selectors
                                 #(eftest.runner/run-tests
                                   (eftest.runner/find-tests selected-namespaces#)
                                   options#))
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
