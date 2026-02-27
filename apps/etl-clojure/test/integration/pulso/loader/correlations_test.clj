(ns pulso.loader.correlations-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [pulso.test-helpers :refer [test-ds with-db-once with-db count-rows]]
            [pulso.loader.correlations :as correlations]
            [pulso.loader.records :as records]
            [clojure.data.xml :as xml]))

(use-fixtures :once with-db-once)
(use-fixtures :each with-db)

(deftest process-correlation-with-nested-records
  (testing "Given correlation batchers, record batchers, and Correlation XML with 2 nested records + metadata"
    (let [corr-batchers (correlations/make-batchers @test-ds 10)
          rec-batchers (records/make-batchers @test-ds 10)
          element (apply xml/element :Correlation
            {:type "HKCorrelationTypeIdentifierBloodPressure"
             :sourceName "iPhone"
             :sourceVersion "15.0"
             :creationDate "2025-01-15 10:00:00 -0300"
             :startDate "2025-01-15 09:00:00 -0300"
             :endDate "2025-01-15 10:00:00 -0300"}
            [(xml/element :MetadataEntry {:key "HKMetadataKeyGroupName" :value "TestGroup"})
             (apply xml/element :Record
               {:type "HKQuantityTypeIdentifierBloodPressureSystolic"
                :sourceName "iPhone"
                :sourceVersion "15.0"
                :value "120"
                :unit "mmHg"
                :creationDate "2025-01-15 10:00:00 -0300"
                :startDate "2025-01-15 09:00:00 -0300"
                :endDate "2025-01-15 10:00:00 -0300"}
               [(xml/element :MetadataEntry {:key "HKMetadataKeySourceSecondaryID" :value "source-1"})])
             (apply xml/element :Record
               {:type "HKQuantityTypeIdentifierBloodPressureDiastolic"
                :sourceName "iPhone"
                :sourceVersion "15.0"
                :value "80"
                :unit "mmHg"
                :creationDate "2025-01-15 10:00:00 -0300"
                :startDate "2025-01-15 09:00:00 -0300"
                :endDate "2025-01-15 10:00:00 -0300"}
               [(xml/element :MetadataEntry {:key "HKMetadataKeySourceSecondaryID" :value "source-2"})])])]

      (testing "When process! is called on both batcher sets, then flush! is called"
        (correlations/process! @test-ds corr-batchers rec-batchers element)
        (correlations/flush! corr-batchers)
        (records/flush! rec-batchers))

      (testing "Then 1 correlation, 2 records, 2 correlation_record join rows, and metadata in both tables"
        (is (= 1 (count-rows @test-ds "correlation")))
        (is (= 2 (count-rows @test-ds "record")))
        (is (= 2 (count-rows @test-ds "correlation_record")))
        (is (= 1 (count-rows @test-ds "correlation_metadata")))
        (is (= 2 (count-rows @test-ds "record_metadata")))))))
