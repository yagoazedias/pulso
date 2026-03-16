(ns pulso.xml.parser
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [pulso.xml.io :as xml-io])
  (:import [java.io BufferedReader]))

(defn parse-health-data
  "Streams the Apple Health XML file and calls handler-fn for each top-level
   element. Uses lazy parsing via StAX so the full document is never in memory.

   handler-fn receives two args: the XML element and the locale string."
  [xml-file handler-fn]
  (log/info "Parsing XML file:" xml-file)
  (with-open [rdr (BufferedReader. (io/reader xml-file))]
    (let [clean-rdr (xml-io/skip-doctype-reader rdr)
          root      (xml/parse clean-rdr)
          locale    (get-in root [:attrs :locale])]
      (log/info "Root tag:" (:tag root) "locale:" locale)
      (doseq [element (:content root)
              :when (map? element)]
        (handler-fn element locale)))))
