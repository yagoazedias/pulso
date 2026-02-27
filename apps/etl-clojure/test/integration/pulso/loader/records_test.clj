(ns pulso.loader.records-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [pulso.test-helpers :refer [test-ds with-db-once with-db count-rows]]
            [pulso.loader.records :as records]
            [clojure.data.xml :as xml]))

(use-fixtures :once with-db-once)
(use-fixtures :each with-db)

(deftest process-record-without-metadata
  (testing "Given record batchers and Record XML element with no metadata"
    (let [batchers (records/make-batchers @test-ds 10)
          element (xml/element :Record
            {:type "HKQuantityTypeIdentifierStepCount"
             :sourceName "iPhone"
             :sourceVersion "15.0"
             :value "1234"
             :unit "count"
             :creationDate "2025-01-15 10:00:00 -0300"
             :startDate "2025-01-15 09:00:00 -0300"
             :endDate "2025-01-15 10:00:00 -0300"})]

      (testing "When process! is called"
        (records/process! @test-ds batchers element))

      (testing "Then record is in buffer (count=1), but 0 rows in DB until flush"
        (is (= 0 (count-rows @test-ds "record")))
        (is (= 1 ((:count (:record batchers)))))))))

(deftest process-record-with-metadata
  (testing "Given record batchers and Record XML element with metadata"
    (let [batchers (records/make-batchers @test-ds 10)
          element (apply xml/element :Record
            {:type "HKQuantityTypeIdentifierHeartRate"
             :sourceName "Apple Watch"
             :sourceVersion "8.0"
             :value "72"
             :unit "count/min"
             :creationDate "2025-01-15 10:00:00 -0300"
             :startDate "2025-01-15 09:00:00 -0300"
             :endDate "2025-01-15 10:00:00 -0300"}
            [(xml/element :MetadataEntry {:key "HKMetadataKeyHeartRateMotionContext" :value "0"})
             (xml/element :MetadataEntry {:key "HKMetadataKeyExternalUUID" :value "abc-123"})])]

      (testing "When process! is called"
        (records/process! @test-ds batchers element))

      (testing "Then 1 record inserted immediately"
        (is (= 1 (count-rows @test-ds "record"))))

      (testing "And when metadata batcher is flushed"
        ((:flush! (:metadata batchers)))

        (testing "Then 2 metadata rows are linked to record"
          (is (= 2 (count-rows @test-ds "record_metadata"))))))))

(deftest flush-clears-pending
  (testing "Given batchers with 2 pending records in buffer"
    (let [batchers (records/make-batchers @test-ds 10)]
      (records/process! @test-ds batchers
        (xml/element :Record
          {:type "HKQuantityTypeIdentifierStepCount"
           :sourceName "iPhone"
           :sourceVersion "15.0"
           :value "1000"
           :unit "count"
           :creationDate "2025-01-15 10:00:00 -0300"
           :startDate "2025-01-15 09:00:00 -0300"
           :endDate "2025-01-15 10:00:00 -0300"}))
      (records/process! @test-ds batchers
        (xml/element :Record
          {:type "HKQuantityTypeIdentifierActiveEnergyBurned"
           :sourceName "iPhone"
           :sourceVersion "15.0"
           :value "100.5"
           :unit "kcal"
           :creationDate "2025-01-15 10:00:00 -0300"
           :startDate "2025-01-15 09:00:00 -0300"
           :endDate "2025-01-15 10:00:00 -0300"}))

      (testing "When flush! is called"
        (records/flush! batchers))

      (testing "Then all records in DB"
        (is (= 2 (count-rows @test-ds "record")))
        (is (= 2 ((:count (:record batchers)))))))))
