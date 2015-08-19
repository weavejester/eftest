(ns eftest.report.progress
  (:require [clojure.test :as test]
            [eftest.runner :as runner]
            [eftest.report.pretty :as pretty]
            [progrock.core :as prog]))

(def ^:private clear-line (apply str "\r" (repeat 80 " ")))

(defn- colored-format [state]
  (str ":progress/:total   :percent% ["
       (if state
         (str (pretty/*fonts* state) ":bar" (pretty/*fonts* :reset))
         ":bar")
       "]  ETA: :remaining"))

(defn- print-progress [{:keys [bar state]}]
  (prog/print bar {:format (colored-format state)}))

(defn- set-state [old-state new-state]
  (case [old-state new-state]
    [nil   :pass]  :pass
    [:pass :fail]  :fail
    [:fail :error] :error
    old-state))

(defmulti report :type)

(defmethod report :default [m])

(defmethod report :begin-test-run [m]
  (test/with-test-out
    (newline)
    (print-progress (reset! runner/*context* {:bar (prog/progress-bar (:count m))}))))

(defmethod report :pass [m]
  (test/with-test-out
    (pretty/report m)
    (print-progress (swap! runner/*context* update-in [:state] set-state :pass))))

(defmethod report :fail [m]
  (test/with-test-out
    (print clear-line)
    (binding [pretty/*divider* "\r"] (pretty/report m))
    (newline)
    (print-progress (swap! runner/*context* update-in [:state] set-state :fail))))

(defmethod report :error [m]
  (test/with-test-out
    (print clear-line)
    (binding [pretty/*divider* "\r"] (pretty/report m))
    (newline)
    (print-progress (swap! runner/*context* update-in [:state] set-state :error))))

(defmethod report :end-test-var [m]
  (test/with-test-out
    (print-progress (swap! runner/*context* update-in [:bar] prog/tick))))

(defmethod report :summary [m]
  (test/with-test-out
    (print-progress (swap! runner/*context* update-in [:bar] prog/done))
    (pretty/report m)))
