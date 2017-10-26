(ns eftest.output-capture
  (:require [clojure.string :as str])
  (:import [java.io OutputStream ByteArrayOutputStream PrintStream PrintWriter]))

(def context (atom {}))

(defn- init-buffer [buffer]
  (or buffer (ByteArrayOutputStream.)))

(defn- get-local-buffer ^ByteArrayOutputStream []
  (let [id (.getId (Thread/currentThread))]
    (-> (swap! context update id init-buffer) (get id))))

(defn- create-proxy-output-stream ^OutputStream []
  (proxy [OutputStream] []
    (write
      ([data]
       (when-let [target (get-local-buffer)]
         (if (instance? Integer data)
           (.write target ^int data)
           (.write target ^bytes data 0 (alength ^bytes data)))))
      ([data off len]
       (when-let [target (get-local-buffer)]
         (.write target data off len))))))

(defn init-capture []
  (reset! context {})
  (let [old-out             System/out
        old-err             System/err
        proxy-output-stream (create-proxy-output-stream)
        new-stream          (PrintStream. proxy-output-stream)
        new-writer          (PrintWriter. proxy-output-stream)]
    (System/setOut new-stream)
    (System/setErr new-stream)
    {:captured-writer new-writer
     :old-system-out  old-out
     :old-system-err  old-err}))

(defn restore-capture [{:keys [old-system-out old-system-err]}]
  (System/setOut old-system-out)
  (System/setErr old-system-err))

(defmacro with-capture [& body]
  `(let [context# (init-capture)
         writer#  (:captured-writer context#)]
     (try
       (binding [*out* writer#, *err* writer#]
         ~@body)
       (finally
         (restore-capture context#)))))

(defn clear-local-output []
  (.reset (get-local-buffer)))

(defn read-local-output []
  (some-> (get-local-buffer) .toByteArray String. str/trim))
