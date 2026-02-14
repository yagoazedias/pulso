(ns pulso.xml.parser
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import [java.io BufferedReader InputStreamReader Reader SequenceInputStream
            StringReader ByteArrayInputStream]))

(defn- chained-reader
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

(defn- skip-doctype-reader
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

(defn parse-health-data
  "Streams the Apple Health XML file and calls handler-fn for each top-level
   element. Uses lazy parsing via StAX so the full document is never in memory.

   handler-fn receives two args: the XML element and the locale string."
  [xml-file handler-fn]
  (log/info "Parsing XML file:" xml-file)
  (with-open [rdr (BufferedReader. (io/reader xml-file))]
    (let [clean-rdr (skip-doctype-reader rdr)
          root      (xml/parse clean-rdr)
          locale    (get-in root [:attrs :locale])]
      (log/info "Root tag:" (:tag root) "locale:" locale)
      (doseq [element (:content root)
              :when (map? element)]
        (handler-fn element locale)))))
