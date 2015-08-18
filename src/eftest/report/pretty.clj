(ns eftest.report.pretty
  (:require [clojure.test :as test]
            [io.aviso.ansi :as ansi]
            [io.aviso.exception :as exception]
            [io.aviso.repl :as repl]))

(def ^:dynamic *fonts*
  {:exception      ansi/red-font
   :reset          ansi/reset-font
   :message        ansi/italic-font
   :property       ansi/bold-font
   :source         ansi/italic-font
   :namespace-name ansi/reset-font
   :function-name  ansi/blue-font
   :clojure-frame  ansi/yellow-font
   :java-frame     ansi/white-font
   :omitted-frame  ansi/reset-font
   :pass           ansi/green-font
   :fail           ansi/red-font
   :error          ansi/red-font})

(defn testing-vars-str [{:keys [file line]}]
  (let [test-var (first test/*testing-vars*)]
    (str (:namespace-name *fonts*) (-> test-var meta :ns ns-name) (:reset *fonts*) "/"
         (:function-name *fonts*) (-> test-var meta :name) (:reset *fonts*)
         (:source *fonts*) " (" file ":" line ")" (:reset *fonts*))))

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :pass [m]
  (test/with-test-out (test/inc-report-counter :pass)))

(defmethod report :fail [{:keys [message expected actual] :as m}]
  (test/with-test-out
    (test/inc-report-counter :fail)
    (newline)
    (println (str (:fail *fonts*) "FAIL" (:reset *fonts*) " in") (testing-vars-str m))
    (when (seq test/*testing-contexts*) (println (test/testing-contexts-str)))
    (when message (println message))
    (println "expected:" (pr-str expected))
    (println "  actual:" (pr-str actual))))

(defmethod report :error [{:keys [message expected actual] :as m}]
  (test/with-test-out
   (test/inc-report-counter :error)
   (newline)
   (println (str (:error *fonts*) "ERROR" (:reset *fonts*) " in") (testing-vars-str m))
   (when (seq test/*testing-contexts*) (println (test/testing-contexts-str)))
   (when message (println message))
   (println "expected:" (pr-str expected))
   (print "  actual: ")
   (if (instance? Throwable actual)
     (binding [exception/*traditional* true, exception/*fonts* *fonts*]
       (repl/pretty-print-stack-trace actual test/*stack-trace-depth*))
     (prn actual))))

(defmethod report :summary [{:keys [test pass fail error]}]
  (let [total (+ pass fail error)
        color (if (= pass total) (:pass *fonts*) (:error *fonts*))]
    (test/with-test-out
      (newline)
      (println "Ran" test "tests containing" total "assertions.")
      (println (str color fail " failures, " error " errors." (:reset *fonts*))))))
