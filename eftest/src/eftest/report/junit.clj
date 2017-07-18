(ns eftest.report.junit
  "A test reporter that outputs JUnit-compatible XML."
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as stack]
            [clojure.test :as test]
            [eftest.report :refer [*context*]]))

;; XML generation based on junit.clj

(def ^:private escape-xml-map
  (zipmap "'<>\"&" (map #(str \& % \;) '[apos lt gt quot amp])))

(defn- escape-xml [text]
  (apply str (map #(escape-xml-map % %) text)))

(defn start-element [tag & [attrs]]
  (print (str "<" tag))
  (if (seq attrs)
    (doseq [[key value] attrs]
      (print (str " " (name key) "=\"" (escape-xml value) "\""))))
  (print ">"))

(defn element-content [content]
  (print (escape-xml content)))

(defn finish-element [tag]
  (print (str "</" tag ">")))

(defn test-name [vars]
  (apply str (interpose "." (reverse (map #(:name (meta %)) vars)))))

(defn package-class [^String name]
  (let [i (.lastIndexOf name ".")]
    (if (< i 0)
      [nil name]
      [(.substring name 0 i) (.substring name (+ i 1))])))

(defn start-case [name classname]
  (start-element 'testcase {:name name :classname classname}))

(defn finish-case []
  (finish-element 'testcase))

(defn suite-attrs [package classname]
  (let [attrs {:name classname}]
    (if package
      (assoc attrs :package package)
      attrs)))

(defn start-suite [name]
  (let [[package classname] (package-class name)]
    (start-element 'testsuite (suite-attrs package classname))))

(defn finish-suite []
  (finish-element 'testsuite))

(defn message-el [tag message expected-str actual-str file line]
  (start-element tag (if message {:message message} {}))
  (element-content
   (let [detail (apply str (interpose
                            "\n"
                            [(str "expected: " expected-str)
                             (str "  actual: " actual-str)
                             (str "      at: " file ":" line)]))]
     (if message (str message "\n" detail) detail)))
  (finish-element tag)
  (println))

(defn failure-el [{:keys [message expected actual file line]}]
  (message-el 'failure message (pr-str expected) (pr-str actual) file line))

(defn error-el [{:keys [message expected actual file line]}]
  (message-el 'error
              message
              (pr-str expected)
              (if (instance? Throwable actual)
                (with-out-str (stack/print-cause-trace actual test/*stack-trace-depth*))
                (prn actual))
              file line))

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :begin-test-run [m]
  (swap! *context* assoc ::test-results {})
  (test/with-test-out
    (println "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    (print "<testsuites>")))

(defmethod report :summary [m]
  (test/with-test-out
    (println "</testsuites>")))

(defmethod report :begin-test-ns [m]
  (test/with-test-out
    (start-suite (name (ns-name (:ns m))))))

(defmethod report :end-test-ns [m]
  (test/with-test-out
    (finish-suite)))

(defmethod report :end-test-var [m]
  (test/with-test-out
    (let [test-var (:var m)
          results  (get-in @*context* [::test-results test-var])]
      (start-case (test-name test/*testing-vars*)
                  (name (ns-name (:ns (meta test-var)))))
      (doseq [result results]
        (if (= :fail (:type result))
          (failure-el result)
          (error-el result)))
      (finish-case)
      (swap! *context* update ::test-results dissoc test-var))))

(defn- push-result [result]
  (let [test-var (first test/*testing-vars*)]
    (swap! *context* update-in [::test-results test-var] conj result)))

(defmethod report :pass [m]
  (test/inc-report-counter :pass))

(defmethod report :fail [m]
  (test/inc-report-counter :fail)
  (push-result m))

(defmethod report :error [m]
  (test/inc-report-counter :error)
  (push-result m))

;; Ignore reports from test.check.
(defmethod report :clojure.test.check.clojure-test/trial [m])
(defmethod report :clojure.test.check.clojure-test/shrinking [m])
(defmethod report :clojure.test.check.clojure-test/shrunk [m])
