(ns eftest.report.pretty
  (:require [clojure.test :as test]
            [clojure.stacktrace :as stack]
            [io.aviso.ansi :as ansi]))

(defn testing-vars-str [{:keys [file line]}]
  (str (ansi/bold (-> test/*testing-vars* first str (subs 2)))
       " (" file ":" line ")"))

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :pass [m]
  (test/with-test-out (test/inc-report-counter :pass)))

(defmethod report :fail [{:keys [message expected actual] :as m}]
  (test/with-test-out
    (test/inc-report-counter :fail)
    (newline)
    (println (str (ansi/red "FAIL") " in") (testing-vars-str m))
    (when (seq test/*testing-contexts*) (println (test/testing-contexts-str)))
    (when message (println message))
    (println "expected:" (pr-str expected))
    (println "  actual:" (pr-str actual))))

(defmethod report :error [{:keys [message expected actual] :as m}]
  (test/with-test-out
   (test/inc-report-counter :error)
   (newline)
   (println (str (ansi/red "ERROR") " in") (testing-vars-str m))
   (when (seq test/*testing-contexts*) (println (test/testing-contexts-str)))
   (when message (println message))
   (println "expected:" (pr-str expected))
   (print "  actual: ")
   (if (instance? Throwable actual)
     (stack/print-cause-trace actual test/*stack-trace-depth*)
     (prn actual))))

(defmethod report :summary [{:keys [test pass fail error]}]
  (let [total (+ pass fail error)
        color (if (= pass total) ansi/green ansi/red)]
    (test/with-test-out
      (newline)
      (println "Ran" test "tests containing" total "assertions.")
      (println (color (str fail " failures, " error " errors."))))))
