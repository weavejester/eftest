(ns eftest.main
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.reader.edn :as edn]
            [eftest.report.junit :as junit]
            [eftest.runner :as runner]))


(def +default-options+
  {:directories ["test"]
   :fail-fast? false
   :capture-output? true
   :multithread? true})

(defn- update-in-for-coll
  [m f]
  (reduce (fn [acc [k v]]
            (assoc acc k (if (coll? v) (f v) v)))
          {} m))

(defn- keywords->var-filters
  [keywords]
  (into #{}
        (map (fn [kw] `(or (contains? (meta ~'%) ~kw)
                           (contains? (meta (:ns (meta ~'%))) ~kw))))
        keywords))

(defn- normalize-options
  [options]
  (-> options
      (update-in-for-coll set)
      (update :directories #(into #{} (map io/file) %))
      (update :included-ns-regexs #(into #{} (map re-pattern) %))
      (update :excluded-ns-regexs #(into #{} (map re-pattern) %))
      (as-> m
        (assoc m :meta-keywords-as-var-filters
               (keywords->var-filters (:meta-keywords m))))
      (as-> m
        (update m :var-filters
                #(into % (:meta-keywords-as-var-filters m))))
      (cond-> (:junit-output options) (assoc :report junit/report))))

(defn- find-namespaces
  [directories]
  (into #{}
       (mapcat #(find/find-namespaces-in-dir %))
       directories))

(defn- filter-namespaces-with-names
  [included-namespaces excluded-namespaces namespaces]
  (let [incl-ns? (or included-namespaces (constantly true))
        excl-ns? (or excluded-namespaces (constantly false))]
    (into #{}
          (comp (filter incl-ns?)
                (remove excl-ns?))
          namespaces)))

(defn- ns-match-some-regexs?
  [regexs a-namespace]
  (some #(re-matches % (name a-namespace)) regexs))

(defn- filter-namespaces-with-regexs
  [included-ns-regexs excluded-ns-regexs namespaces]
  (let [incl-ns-regexs (if (empty? included-ns-regexs)
                         [#".+"]
                         included-ns-regexs)
        excl-ns-regexs (if (empty? excluded-ns-regexs)
                         [#""]
                         excluded-ns-regexs)]
    (into #{}
          (comp (filter #(ns-match-some-regexs? incl-ns-regexs %))
                (remove #(ns-match-some-regexs? excl-ns-regexs %)))
          namespaces)))

(defn- filter-namespaces
  [{:keys [included-namespaces excluded-namespaces
           included-ns-regexs  excluded-ns-regexs] :as options}
   namespaces]
  (if (every? empty? [included-namespaces excluded-namespaces
                      included-ns-regexs excluded-ns-regexs])
    namespaces
    (let [ns-set1 (if (and (empty? included-namespaces)
                           (empty? excluded-namespaces))
                    #{}
                    (filter-namespaces-with-names included-namespaces
                                                  excluded-namespaces
                                                  namespaces))
          ns-set2 (if (and (empty? included-ns-regexs)
                           (empty? excluded-ns-regexs))
                    #{}
                    (filter-namespaces-with-regexs included-ns-regexs
                                                   excluded-ns-regexs
                                                   namespaces))]
    (into ns-set1 ns-set2))))

(defn- find-test-vars
  [namespaces]
  (doseq [n namespaces]
    (require n :reload))
  (into #{}
        (comp (mapcat ns-publics)
              (map #(nth % 1))
              (filter #(contains? (meta %) :test)))
        namespaces))

(defn- filter-test-vars
  [{:keys [var-filters] :as options} vars]
  (if (empty? var-filters)
    vars
    (let [select-var? `(~'fn [~'%] (and ~@var-filters))
          select-var? (eval select-var?)]
      (into []
            (filter select-var?)
            vars))))

(defn find+run-tests
  "REPL friendly function to find and run tests"
  [options]
  (let [options (normalize-options (merge +default-options+ options))
        all-namespaces (find-namespaces (:directories options))
        namespaces (filter-namespaces options all-namespaces)
        all-test-vars (find-test-vars namespaces)
        test-vars (filter-test-vars options all-test-vars)]
    (runner/run-tests test-vars options)))

(defn- assoc-in-vec
  [m k v]
  (update-in m [k] (fnil conj []) v))

(defn- string->boolean
  [str]
  (boolean (re-matches #"(?i)1|yes|true" str)))

(def cli-options
  [["-d" "--directory DIR" "directory to look for namespaces, default: \"test\""
    :validate [seq "directory name cannot be empty"]
    :assoc-fn assoc-in-vec
    :id :directories]
   ["-n" "--ns-include NS" "namespace included for search of test vars"
    :parse-fn symbol
    :assoc-fn assoc-in-vec
    :id :included-namespaces]
   ["-e" "--ns-exclude NS" "namespace excluded for search of test vars"
    :parse-fn symbol
    :assoc-fn assoc-in-vec
    :id :excluded-namespaces]
   ["-I" "--ns-include-re REGEX" "regex to include test namespaces"
    :assoc-fn assoc-in-vec
    :id :included-ns-regexs]
   ["-X" "--ns-exclude-re REGEX" "regex to exclude test namespaces"
    :assoc-fn assoc-in-vec
    :id :excluded-ns-regexs]
   ["-k" "--keyword KEYWORD" "keyword to filter namespaces or test vars, ex: \":integration\""
    :parse-fn edn/read-string
    :validate [keyword? "must be a keyword"]
    :assoc-fn assoc-in-vec
    :id :meta-keywords]
   [nil "--filter EXPR" "expression to filter test vars, a clojure expression with the var bounded to %"
    :parse-fn edn/read-string
    :assoc-fn assoc-in-vec
    :id :var-filters]
   [nil "--junit-output" "output a JUnit XML report"]
   [nil "--fail-fast BOOL" "stop after first failure or error, default false"
    :parse-fn string->boolean
    :id :fail-fast?]
   [nil "--capture-output BOOL" "catch test output and print it only if the test fails, default: true"
    :parse-fn string->boolean
    :id :capture-output?]
   [nil "--multithread BOOL-or-KEYWORD" "one of: true, false, :namespaces or :vars, default: true"
    :parse-fn edn/read-string
    :validate [#(or (instance? Boolean %) (#{:namespaces :vars} %)) "must be a boolean or specific keyword"]
    :id :multithread?]
   [nil "--test-warn-time MILLIS" "print a warning for any test that exceeds given time in milliseconds"
    :parse-fn #(Long/parseLong %)]
   ["-h" "--help"]])

(defn- humain-error
  [error]
  (as-> error $
    (re-matches #"(Error [^:]+):.+|(.+)" $)
    (subvec $ 1)
    (apply str $)))

(defn- humain-errors
  [errors]
  (->> errors
       (into [] (map humain-error))
       (string/join "\n")))

(defn- print-args-errors
  [errors]
  (binding [*out* *err*]
    (println (humain-errors errors))))

(defn- print-usage
  [summary]
  ;; TODO: add usage examples
  ;;       and info about cross option behaviours (-n, -e, -I and -X)
  (println summary))

(defn -main
  [& args]
  (let [{:keys [errors summary options]} (cli/parse-opts args cli-options)
        _ (when errors
            (print-args-errors errors)
            (System/exit 1))
        _ (when (:help options)
            (print-usage summary)
            (System/exit 0))

        {:keys [fail error]} (find+run-tests options)
        exit-code (cond
                    (and (pos? fail)
                         (pos? error)) 30
                    (pos? error)       20
                    (pos? fail)        10
                    :else              0)]
    (System/exit exit-code)))
