(ns pulso.loader.lookups-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [pulso.test-helpers :refer [test-ds with-db-once with-db count-rows select-all]]
            [pulso.loader.lookups :as lookups]))

(use-fixtures :once with-db-once)
(use-fixtures :each with-db)

(deftest ensure-source-id-creates-and-caches
  (testing "Given empty source table and cache"
    (testing "When ensure-source-id! called twice with same name/version"
      (let [id1 (lookups/ensure-source-id! @test-ds "iPhone" "15.0")
            id2 (lookups/ensure-source-id! @test-ds "iPhone" "15.0")]

        (testing "Then first call inserts + caches, second returns cached ID (verify 1 DB row)"
          (is (= id1 id2))
          (is (= 1 (count-rows @test-ds "source"))))))))

(deftest ensure-source-id-different-versions
  (testing "Given empty source table"
    (testing "When called with same name but different versions"
      (let [id1 (lookups/ensure-source-id! @test-ds "iPhone" "15.0")
            id2 (lookups/ensure-source-id! @test-ds "iPhone" "16.0")]

        (testing "Then two different IDs returned and 2 rows in DB"
          (is (not= id1 id2))
          (is (= 2 (count-rows @test-ds "source"))))))))

(deftest ensure-source-id-nil-returns-nil
  (testing "Given empty source table"
    (testing "When called with nil name"
      (let [result (lookups/ensure-source-id! @test-ds nil "15.0")]

        (testing "Then returns nil and no DB insert"
          (is (nil? result))
          (is (= 0 (count-rows @test-ds "source"))))))))

(deftest ensure-device-id-creates-and-caches
  (testing "Given empty device table and cache"
    (testing "When called twice with same raw_text"
      (let [id1 (lookups/ensure-device-id! @test-ds "iPhone (User's Device)")
            id2 (lookups/ensure-device-id! @test-ds "iPhone (User's Device)")]

        (testing "Then first call inserts, second returns cached ID"
          (is (= id1 id2))
          (is (= 1 (count-rows @test-ds "device"))))))))

(deftest ensure-unit-id-creates-and-caches
  (testing "Given empty unit table and cache"
    (testing "When called twice with same unit name"
      (let [id1 (lookups/ensure-unit-id! @test-ds "count/min")
            id2 (lookups/ensure-unit-id! @test-ds "count/min")]

        (testing "Then first call inserts, second returns cached ID"
          (is (= id1 id2))
          (is (= 1 (count-rows @test-ds "unit"))))))))
