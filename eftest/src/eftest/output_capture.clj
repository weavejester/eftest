(ns eftest.output-capture
  (:require [clojure.string :as str])
  (:import [java.io OutputStream ByteArrayOutputStream PrintStream PrintWriter]))

(def local-buffer (ThreadLocal.))

(defn get-local-buffer ^ByteArrayOutputStream []
  (or (.get local-buffer)
      (.get (doto local-buffer (.set (ByteArrayOutputStream.))))))

(defn clear-local-buffer []
  (.reset (get-local-buffer)))

(defn read-local-buffer []
  (String. (.toByteArray (get-local-buffer))))

(defn- create-proxy-output-stream ^OutputStream []
  (proxy [OutputStream] []
    (write
      ([data]
       (let [target (get-local-buffer)]
         (if (instance? Integer data)
           (.write target ^int data)
           (.write target ^bytes data 0 (alength ^bytes data)))))
      ([data off len]
       (.write (get-local-buffer) data off len)))))

(defn init-capture []
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
