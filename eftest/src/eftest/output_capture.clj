(ns eftest.output-capture
  (:require [clojure.string :as str])
  (:import (java.io OutputStream ByteArrayOutputStream PrintStream PrintWriter)
           (java.nio.charset StandardCharsets)))

(def captured-output-contexts (atom {}))

(defn thread-id []
  (-> (Thread/currentThread)
      (.getId)))

(defn init-capture-buffer [buffer]
  (or buffer (ByteArrayOutputStream.)))

(defn get-capture-buffer ^ByteArrayOutputStream []
  (let [id (thread-id)]
    (-> (swap! captured-output-contexts update id init-capture-buffer)
        (get id))))

(defn flush-captured-output ^String []
  (let [output (some-> (get-capture-buffer)
                       .toByteArray
                       (String. StandardCharsets/UTF_8)
                       str/trim)]
    (when-not (str/blank? output)
      output)))

(defn create-proxy-output-stream ^OutputStream []
  (proxy [OutputStream] []
    (write
      ([data]
       (when-let [target (get-capture-buffer)]
         (if (instance? Integer data)
           (.write target ^int data)
           (.write target ^bytes data 0 (alength ^bytes data)))))
      ([data off len]
       (when-let [target (get-capture-buffer)]
         (.write target data off len))))))

(defn init-capture []
  (let [old-out System/out
        old-err System/err
        proxy-output-stream (create-proxy-output-stream)
        new-stream (PrintStream. proxy-output-stream)
        new-writer (PrintWriter. proxy-output-stream)]
    (System/setOut new-stream)
    (System/setErr new-stream)
    {:captured-writer new-writer
     :old-system-out old-out
     :old-system-err old-err}))

(defn restore-capture [{:keys [old-system-out old-system-err]}]
  (System/setOut old-system-out)
  (System/setErr old-system-err))
