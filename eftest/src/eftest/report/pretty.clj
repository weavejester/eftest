(ns eftest.report.pretty
  "A test reporter with an emphasis on pretty formatting."
  (:require [clojure.test :as test]
            [clojure.data :as data]
            [io.aviso.ansi :as ansi]
            [io.aviso.exception :as exception]
            [io.aviso.repl :as repl]
            [puget.printer :as puget]
            [fipp.engine :as fipp]
            [eftest.output-capture :as capture]
            [eftest.report :as report]
            [clojure.string :as str]))

(def ^:dynamic *fonts*
  "The ANSI codes to use for reporting on tests."
  {:exception      ansi/red-font
   :reset          ansi/reset-font
   :message        ansi/italic-font
   :property       ansi/bold-font
   :source         ansi/italic-font
   :function-name  ansi/blue-font
   :clojure-frame  ansi/white-font
   :java-frame     ansi/reset-font
   :omitted-frame  ansi/reset-font
   :pass           ansi/green-font
   :fail           ansi/red-font
   :error          ansi/red-font
   :divider        ansi/yellow-font})

(def ^:dynamic *divider*
  "The divider to use between test failure and error reports."
  "\n")

(defn- testing-scope-str [{:keys [file line]}]
  (let [[ns scope] report/*testing-path*]
    (str
     (cond
       (keyword? scope)
       (str (:clojure-frame *fonts*) (ns-name ns) (:reset *fonts*) " during "
            (:function-name *fonts*) scope (:reset *fonts*))

       (var? scope)
       (str (:clojure-frame *fonts*) (ns-name ns) "/"
            (:function-name *fonts*) (:name (meta scope)) (:reset *fonts*)))
     (when (or file line)
       (str " (" (:source *fonts*) file ":" line (:reset *fonts*) ")")))))

(defn- diff-all [expected actuals]
  (map vector actuals (map #(take 2 (data/diff expected %)) actuals)))

(defn- pretty-printer []
  (puget/pretty-printer {:print-color true
                         :print-meta false}))

(defn- pprint-document [doc]
  (fipp/pprint-document doc {:width 80}))

(defn- equals-fail-report [{:keys [actual]}]
  (let [[_ [_ expected & actuals]] actual
        p (pretty-printer)]
    (doseq [[actual [a b]] (diff-all expected actuals)]
      (pprint-document
        [:group
         [:span "expected: " (puget/format-doc p expected) :break]
         [:span "  actual: " (puget/format-doc p actual) :break]
         (when (and (not= expected a) (not= actual b))
           [:span "    diff: "
            (if a
              [:span "- " (puget/format-doc p a) :break])
            (if b
              [:span
               (if a  "          + " "+ ")
               (puget/format-doc p b)])])]))))

(defn- predicate-fail-report [{:keys [expected actual]}]
  (let [p (pretty-printer)]
    (pprint-document
      [:group
       [:span "expected: " (puget/format-doc p expected) :break]
       [:span "  actual: " (puget/format-doc p actual)]])))

(defn- print-stacktrace [t]
  (binding [exception/*traditional* true
            exception/*fonts* *fonts*]
    (repl/pretty-print-stack-trace t test/*stack-trace-depth*)))

(defn- error-report [{:keys [expected actual]}]
  (if expected
    (let [p (pretty-printer)]
      (pprint-document
       [:group
        [:span "expected: " (puget/format-doc p expected) :break]
        [:span "  actual: " (with-out-str (print-stacktrace actual))]]))
    (print-stacktrace actual)))

(defn- print-output [output]
  (let [c (:divider *fonts*)
        r (:reset *fonts*)]
    (when-not (str/blank? output)
      (println (str c "---" r " Test output " c "---" r))
      (println (str/trim-newline output))
      (println (str c "-------------------" r)))))

(defmulti report
  "A reporting function compatible with clojure.test. Uses ANSI colors and
  terminal formatting to produce readable and 'pretty' reports."
  :type)

(defmethod report :default [m])

(defmethod report :pass [m]
  (test/with-test-out (test/inc-report-counter :pass)))

(defmethod report :fail [{:keys [message expected] :as m}]
  (test/with-test-out
    (test/inc-report-counter :fail)
    (print *divider*)
    (println (str (:fail *fonts*) "FAIL" (:reset *fonts*) " in") (testing-scope-str m))
    (when (seq test/*testing-contexts*) (println (test/testing-contexts-str)))
    (when message (println message))
    (if (and (sequential? expected)
             (= (first expected) '=))
      (equals-fail-report m)
      (predicate-fail-report m))
    (print-output (capture/read-test-buffer))))

(defmethod report :error [{:keys [message expected actual] :as m}]
  (test/with-test-out
    (test/inc-report-counter :error)
    (print *divider*)
    (println (str (:error *fonts*) "ERROR" (:reset *fonts*) " in") (testing-scope-str m))
    (when (seq test/*testing-contexts*) (println (test/testing-contexts-str)))
    (when message (println message))
    (error-report m)
    (some-> (capture/read-test-buffer) (print-output))))

(defn- pluralize [word count]
  (if (= count 1) word (str word "s")))

(defn- format-interval [duration]
  (format "%.3f seconds" (double (/ duration 1e3))))

(defmethod report :long-test [{:keys [duration] :as m}]
  (test/with-test-out
    (print *divider*)
    (println (str (:fail *fonts*) "LONG TEST" (:reset *fonts*) " in") (testing-scope-str m))
    (when duration (println "Test took" (format-interval duration) "seconds to run"))))

(defmethod report :summary [{:keys [test pass fail error duration]}]
  (let [total (+ pass fail error)
        color (if (= pass total) (:pass *fonts*) (:error *fonts*))]
    (test/with-test-out
      (print *divider*)
      (println "Ran" test "tests in" (format-interval duration))
      (println (str color
                    total " " (pluralize "assertion" total) ", "
                    fail  " " (pluralize "failure" fail) ", "
                    error " " (pluralize "error" error) "."
                    (:reset *fonts*))))))
