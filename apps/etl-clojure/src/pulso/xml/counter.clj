(ns pulso.xml.counter
  "Fast element counter using StAX (XMLStreamReader) directly.
   Counts START_ELEMENT events at depth 2 (direct children of <HealthData>)
   without any attribute parsing or object allocation per element."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [pulso.xml.io :as xml-io])
  (:import [java.io BufferedReader]
           [javax.xml.stream XMLInputFactory XMLStreamConstants XMLStreamReader]))

(defn count-elements
  "Scans the XML file and returns a map of element tag keywords to counts.
   Only counts direct children of the root element (depth 2).
   Example: {:Record 385421, :Workout 312, :Correlation 89, :ActivitySummary 365}"
  [xml-file]
  (log/info "Counting elements in:" xml-file)
  (let [start   (System/currentTimeMillis)
        factory (doto (XMLInputFactory/newInstance)
                  (.setProperty XMLInputFactory/IS_VALIDATING false)
                  (.setProperty XMLInputFactory/IS_NAMESPACE_AWARE false)
                  (.setProperty XMLInputFactory/SUPPORT_DTD false)
                  (.setProperty XMLInputFactory/IS_SUPPORTING_EXTERNAL_ENTITIES false))
        counts  (java.util.HashMap.)
        depth   (volatile! 0)]
    (with-open [rdr (BufferedReader. (io/reader xml-file))]
      (let [clean-rdr (xml-io/skip-doctype-reader rdr)
            ^XMLStreamReader sr (.createXMLStreamReader factory clean-rdr)]
        (try
          (while (.hasNext sr)
            (let [event (.next sr)]
              (case event
                1 ;; XMLStreamConstants/START_ELEMENT
                (do (vswap! depth inc)
                    (when (= @depth 2)
                      (let [tag (.getLocalName sr)]
                        (.put counts tag
                              (unchecked-inc (long (.getOrDefault counts tag 0)))))))

                2 ;; XMLStreamConstants/END_ELEMENT
                (vswap! depth dec)

                nil)))
          (finally
            (.close sr)))))
    (let [result (persistent!
                   (reduce (fn [m ^java.util.Map$Entry e]
                             (assoc! m (keyword (.getKey e)) (.getValue e)))
                           (transient {})
                           (.entrySet counts)))
          elapsed (- (System/currentTimeMillis) start)]
      (log/info "Element counting complete" {:counts result :elapsed-ms elapsed})
      result)))
