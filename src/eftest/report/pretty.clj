(ns eftest.report.pretty
  (:require [clojure.test :as test]
            [clojure.data :as data]
            [io.aviso.ansi :as ansi]
            [io.aviso.exception :as exception]
            [io.aviso.repl :as repl]
            [puget.printer :as puget]))

(def ^:dynamic *fonts*
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
   :error          ansi/red-font})

(defn testing-vars-str [{:keys [file line]}]
  (let [test-var (first test/*testing-vars*)]
    (str (:clojure-frame *fonts*) (-> test-var meta :ns ns-name) "/"
         (:function-name *fonts*) (-> test-var meta :name) (:reset *fonts*)
         " (" (:source *fonts*) file ":" line (:reset *fonts*) ")")))

(defn- diff-all [expected actuals]
  (map vector actuals (map #(take 2 (data/diff expected %)) actuals)))

(defn- equals-fail-report [{:keys [actual]}]
  (let [[_ [_ expected & actuals]] actual]
    (doseq [[actual [a b]] (diff-all expected actuals)]
      (println "expected:" (puget/cprint-str expected))
      (println "  actual:" (puget/cprint-str actual))
      (print "    diff:")
      (if a
        (do (print " - ")
            (puget/cprint a)
            (print "          + "))
        (print " + "))
      (if b
        (puget/cprint b)))))

(defn- predicate-fail-report [{:keys [expected actual]}]
  (println "expected:" (puget/cprint-str expected))
  (println "  actual:" (puget/cprint-str actual)))

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :pass [m]
  (test/with-test-out (test/inc-report-counter :pass)))

(defmethod report :fail [{:keys [message expected] :as m}]
  (test/with-test-out
    (test/inc-report-counter :fail)
    (newline)
    (println (str (:fail *fonts*) "FAIL" (:reset *fonts*) " in") (testing-vars-str m))
    (when (seq test/*testing-contexts*) (println (test/testing-contexts-str)))
    (when message (println message))
    (if (= (first expected) '=)
      (equals-fail-report m)
      (predicate-fail-report m))))

(defmethod report :error [{:keys [message expected actual] :as m}]
  (test/with-test-out
   (test/inc-report-counter :error)
   (newline)
   (println (str (:error *fonts*) "ERROR" (:reset *fonts*) " in") (testing-vars-str m))
   (when (seq test/*testing-contexts*) (println (test/testing-contexts-str)))
   (when message (println message))
   (println "expected:" (puget/cprint-str expected))
   (print "  actual: ")
   (if (instance? Throwable actual)
     (binding [exception/*traditional* true, exception/*fonts* *fonts*]
       (repl/pretty-print-stack-trace actual test/*stack-trace-depth*))
     (puget/cprint actual))))

(defmethod report :summary [{:keys [test pass fail error]}]
  (let [total (+ pass fail error)
        color (if (= pass total) (:pass *fonts*) (:error *fonts*))]
    (test/with-test-out
      (newline)
      (println "Ran" test "tests containing" total "assertions.")
      (println (str color fail " failures, " error " errors." (:reset *fonts*))))))
