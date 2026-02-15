(ns pulso.loader.batch-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [pulso.test-helpers :refer [test-ds with-db-once with-db count-rows]]
            [pulso.loader.batch :as batch])
  (:import [java.time LocalDate]))

(use-fixtures :once with-db-once)
(use-fixtures :each with-db)

(deftest batcher-flushes-at-batch-size
  (testing "Given a batcher with batch-size of 2"
    (let [batcher (batch/make-batcher @test-ds "activity_summary"
                    [:date_components :active_energy_burned] 2)]

      (testing "When 3 rows are added"
        ((:add! batcher) [(LocalDate/of 2025 1 1) 100.0])
        ((:add! batcher) [(LocalDate/of 2025 1 2) 200.0])
        ((:add! batcher) [(LocalDate/of 2025 1 3) 300.0]))

      (testing "Then first 2 rows are flushed to DB and 1 remains pending"
        (is (= 2 (count-rows @test-ds "activity_summary")))
        (is (= 3 ((:count batcher))))))))

(deftest batcher-flush-partial
  (testing "Given a batcher with 1 row in buffer"
    (let [batcher (batch/make-batcher @test-ds "activity_summary"
                    [:date_components :active_energy_burned] 5)]
      ((:add! batcher) [(LocalDate/of 2025 1 1) 100.0])

      (testing "When manual flush is called"
        ((:flush! batcher)))

      (testing "Then row is inserted to DB"
        (is (= 1 (count-rows @test-ds "activity_summary")))
        (is (= 1 ((:count batcher))))))))

(deftest batcher-empty-flush-noop
  (testing "Given an empty batcher"
    (let [batcher (batch/make-batcher @test-ds "activity_summary"
                    [:date_components :active_energy_burned] 5)]

      (testing "When flush is called on empty batcher"
        ((:flush! batcher)))

      (testing "Then no error occurs and DB is unchanged"
        (is (= 0 (count-rows @test-ds "activity_summary")))
        (is (= 0 ((:count batcher))))))))
