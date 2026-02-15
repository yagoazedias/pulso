(ns pulso.etl-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [pulso.test-helpers :refer [test-ds with-db-once with-db count-rows]]
            [pulso.etl :as etl]
            [clojure.java.io :as io]))

(use-fixtures :once with-db-once)
(use-fixtures :each with-db)

(deftest execute-full-pipeline
  (testing "Given test database and small-export.xml fixture"
    (let [fixture-path (.getAbsolutePath (io/file (io/resource "fixtures/small-export.xml")))]
      (testing "When execute! is called with fixture path"
        (let [result (etl/execute! @test-ds fixture-path 5000)]
          (testing "Then returned counts match expected (only top-level items)"
            ;; Fixture contains: 2 top-level records (nested records not counted separately)
            (is (= 2 (:records result)))
            (is (= 1 (:workouts result)))
            (is (= 1 (:correlations result)))
            (is (= 1 (:activities result))))

          (testing "And all tables have correct row counts (including nested records)"
            ;; Records: 2 top-level + 2 nested in correlation = 4 total
            (is (= 4 (count-rows @test-ds "record")))
            ;; Record metadata: 2 from HeartRate, 0 from ActiveEnergyBurned, 0 from nested records
            (is (= 2 (count-rows @test-ds "record_metadata")))
            ;; Workouts
            (is (= 1 (count-rows @test-ds "workout")))
            (is (= 1 (count-rows @test-ds "workout_metadata")))
            (is (= 1 (count-rows @test-ds "workout_event")))
            (is (= 1 (count-rows @test-ds "workout_statistics")))
            (is (= 1 (count-rows @test-ds "workout_route")))
            ;; Correlations
            (is (= 1 (count-rows @test-ds "correlation")))
            (is (= 1 (count-rows @test-ds "correlation_metadata")))
            (is (= 2 (count-rows @test-ds "correlation_record")))
            ;; Activity
            (is (= 1 (count-rows @test-ds "activity_summary")))
            ;; User profile
            (is (= 1 (count-rows @test-ds "user_profile")))))))))

(deftest execute-is-idempotent
  (testing "Given ETL already run once on fixture"
    (let [fixture-path (.getAbsolutePath (io/file (io/resource "fixtures/small-export.xml")))
          result1 (etl/execute! @test-ds fixture-path 5000)]
      (testing "When execute! is called second time on same fixture"
        (let [result2 (etl/execute! @test-ds fixture-path 5000)]
          (testing "Then all counts identical (verifies truncate-and-reload strategy)"
            (is (= (:records result1) (:records result2)))
            (is (= (:workouts result1) (:workouts result2)))
            (is (= (:correlations result1) (:correlations result2)))
            (is (= (:activities result1) (:activities result2)))
            ;; Also verify row counts in database match both runs
            (is (= 4 (count-rows @test-ds "record")))
            (is (= 1 (count-rows @test-ds "workout")))
            (is (= 1 (count-rows @test-ds "correlation")))
            (is (= 1 (count-rows @test-ds "activity_summary")))
            (is (= 1 (count-rows @test-ds "user_profile")))))))))
