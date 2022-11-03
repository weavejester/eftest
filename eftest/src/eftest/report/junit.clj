(ns eftest.report.junit
  "A test reporter that outputs JUnit-compatible XML."
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as stack]
            [clojure.test :as test]
            [eftest.report :refer [*context*]]
            [eftest.report :as report]))

;; XML generation based on junit.clj

(defn- combine [f g]
  (fn [] (g) (f)))

(def ^:private flush-lock (Object.))

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

(defn package-class [name]
  (let [i (.lastIndexOf name ".")]
    (if (< i 0)
      [nil name]
      [(.substring name 0 i) (.substring name (+ i 1))])))

(defn start-case [name classname time]
  (start-element 'testcase {:name name :classname classname :time time}))

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
  (let [ns (name (ns-name (:ns m)))
        f  #(test/with-test-out (start-suite ns))]
    (swap! *context* assoc-in [::deferred-report ns] f)))

(defmethod report :end-test-ns [m]
  (let [ns (name (ns-name (:ns m)))
        g  (get-in @*context* [::deferred-report ns])
        f  #(test/with-test-out (finish-suite))]
    (locking flush-lock (g) (f))
    (swap! *context* update ::deferred-report dissoc ns)))

(defmethod report :begin-test-var [m]
  (swap! *context* assoc-in [::test-start-times (:var m)] (System/nanoTime)))

(defmethod report :end-test-var [m]
  (let [ns (-> (:var m) meta :ns ns-name name)
        duration (- (System/nanoTime)
                    (get-in @*context* [::test-start-times (:var m)]))
        testing-vars test/*testing-vars*
        f  #(test/with-test-out
              (let [test-var (:var m)
                    time (format "%.03f" (/ duration 1e9))
                    results  (get-in @*context* [::test-results test-var])]
                (start-case (test-name testing-vars) ns time)
                (doseq [result results]
                  (if (= :fail (:type result))
                    (failure-el result)
                    (error-el result)))
                (finish-case)
                (swap! *context* update ::test-results dissoc test-var)))]
    (swap! *context* update-in [::deferred-report ns] (partial combine f))))

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
