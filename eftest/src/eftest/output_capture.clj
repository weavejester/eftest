(ns eftest.output-capture
  (:require [clojure.string :as str])
  (:import [java.io OutputStream ByteArrayOutputStream PrintStream PrintWriter]))

(def ^:dynamic *test-buffer* nil)

(defn read-test-buffer []
  (some-> *test-buffer* (.toByteArray) (String.)))

(def active-buffers (atom #{}))

(defmacro with-test-buffer [& body]
  `(let [buffer# (ByteArrayOutputStream.)]
     (try
       (swap! active-buffers conj buffer#)
       (binding [*test-buffer* buffer#] ~@body)
       (finally (swap! active-buffers disj buffer#)))))

(defn- doto-capture-buffer [f]
  (if *test-buffer*
    (f *test-buffer*)
    (doseq [buffer @active-buffers]
      (f buffer))))

(defn- create-proxy-output-stream ^OutputStream []
  (proxy [OutputStream] []
    (write
      ([data]
       (if (instance? Integer data)
         (doto-capture-buffer #(.write % ^int data))
         (doto-capture-buffer #(.write % ^bytes data 0 (alength ^bytes data)))))
      ([data off len]
       (doto-capture-buffer #(.write % data off len))))))

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
         (with-redefs [*out* writer#, *err* writer#]
           ~@body))
       (finally
         (restore-capture context#)))))
