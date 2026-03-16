(ns pulso.loader.workouts-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [pulso.test-helpers :refer [test-ds with-db-once with-db count-rows]]
            [pulso.loader.workouts :as workouts]
            [clojure.data.xml :as xml]))

(use-fixtures :once with-db-once)
(use-fixtures :each with-db)

(deftest process-workout-all-children
  (testing "Given workout batchers and Workout XML with metadata, event, statistics, route"
    (let [batchers (workouts/make-batchers @test-ds 10)
          element (apply xml/element :Workout
            {:workoutActivityType "HKWorkoutActivityTypeRunning"
             :duration "60.0"
             :durationUnit "min"
             :totalDistance "10.5"
             :totalDistanceUnit "km"
             :totalEnergyBurned "500.0"
             :totalEnergyBurnedUnit "kcal"
             :sourceName "iPhone"
             :sourceVersion "15.0"
             :device "iPhone (User's Device)"
             :creationDate "2025-01-15 10:00:00 -0300"
             :startDate "2025-01-15 09:00:00 -0300"
             :endDate "2025-01-15 10:00:00 -0300"}
            [(xml/element :MetadataEntry {:key "HKMetadataKeyWorkoutBrandName" :value "Apple"})
             (xml/element :WorkoutEvent {:type "HKWorkoutEventTypePause" :date "2025-01-15 09:30:00 -0300" :duration "5.0" :durationUnit "min"})
             (xml/element :WorkoutStatistics {:type "HKQuantityTypeIdentifierHeartRateVariabilitySDNN" :startDate "2025-01-15 09:00:00 -0300" :endDate "2025-01-15 10:00:00 -0300" :average "45.0" :minimum "30.0" :maximum "60.0" :sum "45.0" :unit "ms"})
             (apply xml/element :WorkoutRoute
               {:sourceName "Apple Watch" :startDate "2025-01-15 09:00:00 -0300" :endDate "2025-01-15 10:00:00 -0300"}
               [(xml/element :FileReference {:path "/path/to/route.gpx"})])])]

      (testing "When process! is called, then flush! is called"
        (workouts/process! @test-ds batchers element)
        (workouts/flush! batchers))

      (testing "Then 1 workout, 1 metadata, 1 event, 1 statistics, 1 route row inserted"
        (is (= 1 (count-rows @test-ds "workout")))
        (is (= 1 (count-rows @test-ds "workout_metadata")))
        (is (= 1 (count-rows @test-ds "workout_event")))
        (is (= 1 (count-rows @test-ds "workout_statistics")))
        (is (= 1 (count-rows @test-ds "workout_route")))))))

(deftest process-workout-no-children
  (testing "Given workout batchers and minimal Workout XML (no children)"
    (let [batchers (workouts/make-batchers @test-ds 10)
          element (xml/element :Workout
            {:workoutActivityType "HKWorkoutActivityTypeCycling"
             :duration "30.0"
             :durationUnit "min"
             :sourceName "iPhone"
             :sourceVersion "15.0"
             :device "iPhone (User's Device)"
             :creationDate "2025-01-15 10:00:00 -0300"
             :startDate "2025-01-15 09:00:00 -0300"
             :endDate "2025-01-15 09:30:00 -0300"})]

      (testing "When process! is called, then flush! is called"
        (workouts/process! @test-ds batchers element)
        (workouts/flush! batchers))

      (testing "Then 1 workout row inserted and 0 rows in child tables"
        (is (= 1 (count-rows @test-ds "workout")))
        (is (= 0 (count-rows @test-ds "workout_metadata")))
        (is (= 0 (count-rows @test-ds "workout_event")))
        (is (= 0 (count-rows @test-ds "workout_statistics")))
        (is (= 0 (count-rows @test-ds "workout_route")))))))
