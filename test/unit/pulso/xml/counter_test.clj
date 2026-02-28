(ns pulso.xml.counter-test
  (:require [clojure.test :refer [deftest is testing]]
            [pulso.xml.counter :as counter]
            [clojure.java.io :as io]))

(deftest count-elements-fixture
  (let [fixture (.getPath (io/resource "fixtures/small-export.xml"))
        counts  (counter/count-elements fixture)]
    (testing "counts direct children of HealthData"
      (is (= 2 (:Record counts)))
      (is (= 1 (:Workout counts)))
      (is (= 1 (:Correlation counts)))
      (is (= 1 (:ActivitySummary counts)))
      (is (= 1 (:ExportDate counts)))
      (is (= 1 (:Me counts))))
    (testing "does not count nested elements"
      ;; MetadataEntry, WorkoutEvent, etc. are nested — should not appear
      (is (nil? (:MetadataEntry counts)))
      (is (nil? (:WorkoutEvent counts)))
      (is (nil? (:WorkoutStatistics counts))))))

(deftest count-elements-with-doctype
  (let [xml-content "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<!DOCTYPE HealthData [
  <!ATTLIST ExportDate value CDATA #REQUIRED>
]>
<HealthData locale=\"en_US\">
 <ExportDate value=\"2025-01-01 00:00:00 +0000\"/>
 <Record type=\"StepCount\" value=\"100\"
         creationDate=\"2025-01-01\" startDate=\"2025-01-01\" endDate=\"2025-01-01\"/>
 <Record type=\"HeartRate\" value=\"72\"
         creationDate=\"2025-01-01\" startDate=\"2025-01-01\" endDate=\"2025-01-01\"/>
 <Record type=\"Distance\" value=\"1.5\"
         creationDate=\"2025-01-01\" startDate=\"2025-01-01\" endDate=\"2025-01-01\"/>
</HealthData>"
        tmp-file (java.io.File/createTempFile "health-count-" ".xml")]
    (try
      (spit tmp-file xml-content)
      (let [counts (counter/count-elements (.getPath tmp-file))]
        (is (= 3 (:Record counts)))
        (is (= 1 (:ExportDate counts))))
      (finally
        (.delete tmp-file)))))
