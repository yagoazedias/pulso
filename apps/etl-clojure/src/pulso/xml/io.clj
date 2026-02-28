(ns pulso.xml.io
  "Shared XML reader utilities for streaming Apple Health export files.
   Handles DOCTYPE stripping and reader chaining so both the parser
   and the fast counter can reuse the same plumbing."
  (:require [clojure.string :as str])
  (:import [java.io BufferedReader Reader StringReader]))

(defn chained-reader
  "Creates a Reader that reads from reader-a first, then reader-b."
  ^Reader [^Reader reader-a ^Reader reader-b]
  (let [current (volatile! reader-a)
        exhausted (volatile! false)]
    (proxy [Reader] []
      (read
        ([]
         (let [c (.read ^Reader @current)]
           (if (and (= c -1) (not @exhausted))
             (do (vreset! current reader-b)
                 (vreset! exhausted true)
                 (.read ^Reader @current))
             c)))
        ([cbuf off len]
         (let [n (.read ^Reader @current cbuf off len)]
           (if (and (= n -1) (not @exhausted))
             (do (vreset! current reader-b)
                 (vreset! exhausted true)
                 (.read ^Reader @current cbuf off len))
             n))))
      (close []
        (.close reader-a)
        (.close reader-b)))))

(defn skip-doctype-reader
  "Returns a Reader that skips the DOCTYPE declaration block.
   Only buffers the small header (~220 lines), then streams the rest directly.
   The returned reader must be used while the underlying BufferedReader is open."
  ^Reader [^BufferedReader rdr]
  (let [header (StringBuilder.)
        in-doctype (volatile! false)]
    ;; Read through the header, keeping non-DOCTYPE lines
    (loop []
      (let [line (.readLine rdr)]
        (when line
          (let [trimmed (str/trim line)]
            (cond
              (str/starts-with? trimmed "<!DOCTYPE")
              (do (vreset! in-doctype true)
                  (if (str/includes? line "]>")
                    (vreset! in-doctype false)
                    (recur)))

              @in-doctype
              (do (when (str/includes? line "]>")
                    (vreset! in-doctype false))
                  (recur))

              :else
              (do (.append header line)
                  (.append header "\n")
                  ;; Stop once we've seen the HealthData opening tag
                  (when-not (str/includes? trimmed "<HealthData")
                    (recur))))))))
    ;; Combine the cleaned header with the rest of the file (streamed)
    (chained-reader (StringReader. (str header)) rdr)))
