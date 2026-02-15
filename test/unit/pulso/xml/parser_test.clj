(ns pulso.xml.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [pulso.xml.parser :as parser]
            [clojure.java.io :as io]))

(deftest parse-health-data-dispatches-all-elements
  (let [fixture (.getPath (io/resource "fixtures/small-export.xml"))
        collected (atom [])
        locale-seen (atom nil)]
    (parser/parse-health-data fixture
      (fn [element locale]
        (reset! locale-seen locale)
        (swap! collected conj (:tag element))))
    (is (= "pt_BR" @locale-seen))
    ;; Fixture has: ExportDate, Me, Record, Record, Workout, Correlation, ActivitySummary
    (is (= [:ExportDate :Me :Record :Record :Workout :Correlation :ActivitySummary]
           @collected))))

(deftest parse-health-data-with-doctype
  (let [xml-content "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<!DOCTYPE HealthData [
  <!ATTLIST ExportDate value CDATA #REQUIRED>
  <!ATTLIST Record type CDATA #REQUIRED>
]>
<HealthData locale=\"en_US\">
 <ExportDate value=\"2025-01-01 00:00:00 +0000\"/>
 <Record type=\"HKQuantityTypeIdentifierStepCount\"
         sourceName=\"iPhone\"
         value=\"100\"
         creationDate=\"2025-01-01 12:00:00 +0000\"
         startDate=\"2025-01-01 12:00:00 +0000\"
         endDate=\"2025-01-01 12:30:00 +0000\"/>
</HealthData>"
        tmp-file (java.io.File/createTempFile "health-doctype-" ".xml")]
    (try
      (spit tmp-file xml-content)
      (let [tags (atom [])]
        (parser/parse-health-data (.getPath tmp-file)
          (fn [element _locale]
            (swap! tags conj (:tag element))))
        (is (= [:ExportDate :Record] @tags)))
      (finally
        (.delete tmp-file)))))

(deftest parse-health-data-skips-whitespace
  (let [fixture (.getPath (io/resource "fixtures/small-export.xml"))
        non-map-count (atom 0)]
    (parser/parse-health-data fixture
      (fn [element _locale]
        (when-not (map? element)
          (swap! non-map-count inc))))
    ;; All dispatched elements should be maps (whitespace/text nodes filtered)
    (is (= 0 @non-map-count))))
