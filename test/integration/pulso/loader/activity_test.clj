(ns pulso.loader.activity-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [pulso.test-helpers :refer [test-ds with-db-once with-db count-rows select-all]]
            [pulso.loader.activity :as activity]
            [clojure.data.xml :as xml]))

(use-fixtures :once with-db-once)
(use-fixtures :each with-db)

(deftest process-activity-summary
  (testing "Given activity batcher and ActivitySummary XML with all fields"
    (let [batcher (activity/make-batcher @test-ds 10)
          element (xml/element :ActivitySummary
            {:dateComponents "2025-01-15"
             :activeEnergyBurned "450.5"
             :activeEnergyBurnedGoal "420.0"
             :activeEnergyBurnedUnit "kcal"
             :appleMoveTime "30.5"
             :appleMoveTimeGoal "30.0"
             :appleExerciseTime "25.0"
             :appleExerciseTimeGoal "30.0"
             :appleStandHours "10.0"
             :appleStandHoursGoal "12.0"})]

      (testing "When process! is called, then batcher is flushed"
        (activity/process! batcher element)
        ((:flush! batcher)))

      (testing "Then 1 activity_summary row with correct date_components and all goal/value fields"
        (is (= 1 (count-rows @test-ds "activity_summary")))
        (let [rows (select-all @test-ds "activity_summary")
              row (first rows)]
          (is (= "2025-01-15" (str (:activity_summary/date_components row))))
          (is (= 450.5 (:activity_summary/active_energy_burned row)))
          (is (= 420.0 (:activity_summary/active_energy_burned_goal row)))
          (is (= "kcal" (:activity_summary/active_energy_burned_unit row)))
          (is (= 30.5 (:activity_summary/apple_move_time row)))
          (is (= 30.0 (:activity_summary/apple_move_time_goal row)))
          (is (= 25.0 (:activity_summary/apple_exercise_time row)))
          (is (= 30.0 (:activity_summary/apple_exercise_time_goal row)))
          (is (= 10.0 (:activity_summary/apple_stand_hours row)))
          (is (= 12.0 (:activity_summary/apple_stand_hours_goal row))))))))
