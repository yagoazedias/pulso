(ns pulso.xml.transform-test
  (:require [clojure.test :refer [deftest is testing]]
            [pulso.xml.transform :as t])
  (:import [java.time OffsetDateTime LocalDate ZoneOffset]))

;; --- parse-datetime ---

(deftest parse-datetime-valid
  (let [result (t/parse-datetime "2025-01-01 10:00:00 -0300")]
    (is (instance? OffsetDateTime result))
    (is (= 2025 (.getYear result)))
    (is (= 1 (.getMonthValue result)))
    (is (= 1 (.getDayOfMonth result)))
    (is (= 10 (.getHour result)))
    (is (= 0 (.getMinute result)))
    (is (= (ZoneOffset/ofHours -3) (.getOffset result)))))

(deftest parse-datetime-nil-and-empty
  (is (nil? (t/parse-datetime nil)))
  (is (nil? (t/parse-datetime ""))))

;; --- parse-date ---

(deftest parse-date-valid
  (let [result (t/parse-date "1998-10-11")]
    (is (instance? LocalDate result))
    (is (= 1998 (.getYear result)))
    (is (= 10 (.getMonthValue result)))
    (is (= 11 (.getDayOfMonth result)))))

(deftest parse-date-nil-and-empty
  (is (nil? (t/parse-date nil)))
  (is (nil? (t/parse-date ""))))

;; --- parse-dbl ---

(deftest parse-dbl-valid
  (is (= 72.5 (t/parse-dbl "72.5")))
  (is (= 0.0 (t/parse-dbl "0")))
  (is (= -3.14 (t/parse-dbl "-3.14"))))

(deftest parse-dbl-invalid
  (is (nil? (t/parse-dbl "abc")))
  (is (nil? (t/parse-dbl nil)))
  (is (nil? (t/parse-dbl ""))))

;; --- export-date-element->map ---

(deftest export-date-element->map-test
  (let [element {:tag :ExportDate
                 :attrs {:value "2025-06-15 08:30:00 -0300"}}
        result (t/export-date-element->map element)]
    (is (instance? OffsetDateTime (:export-date result)))
    (is (= 2025 (.getYear (:export-date result))))
    (is (= 6 (.getMonthValue (:export-date result))))))

;; --- me-element->map ---

(deftest me-element->map-test
  (let [element {:tag :Me
                 :attrs {:HKCharacteristicTypeIdentifierDateOfBirth "1998-10-11"
                         :HKCharacteristicTypeIdentifierBiologicalSex "HKBiologicalSexMale"
                         :HKCharacteristicTypeIdentifierBloodType "HKBloodTypeAPositive"
                         :HKCharacteristicTypeIdentifierFitzpatrickSkinType "HKFitzpatrickSkinTypeIII"
                         :HKCharacteristicTypeIdentifierCardioFitnessMedicationsUse "HKCardioFitnessMedicationsUseNone"}}
        result (t/me-element->map element "pt_BR")]
    (is (instance? LocalDate (:date-of-birth result)))
    (is (= "HKBiologicalSexMale" (:biological-sex result)))
    (is (= "HKBloodTypeAPositive" (:blood-type result)))
    (is (= "HKFitzpatrickSkinTypeIII" (:fitzpatrick-skin result)))
    (is (= "HKCardioFitnessMedicationsUseNone" (:cardio-fitness-meds result)))
    (is (= "pt_BR" (:locale result)))))

;; --- record-element->map ---

(deftest record-element->map-without-metadata
  (let [element {:tag :Record
                 :attrs {:type "HKQuantityTypeIdentifierActiveEnergyBurned"
                         :sourceName "Apple Watch"
                         :sourceVersion "10.0"
                         :unit "kcal"
                         :value "15.3"
                         :device "<<HKDevice>>"
                         :creationDate "2025-01-15 11:00:00 -0300"
                         :startDate "2025-01-15 11:00:00 -0300"
                         :endDate "2025-01-15 11:15:00 -0300"}
                 :content []}
        result (t/record-element->map element)]
    (is (= "HKQuantityTypeIdentifierActiveEnergyBurned" (:type result)))
    (is (= "Apple Watch" (:source-name result)))
    (is (= "10.0" (:source-version result)))
    (is (= "kcal" (:unit result)))
    (is (= "15.3" (:value result)))
    (is (instance? OffsetDateTime (:creation-date result)))
    (is (instance? OffsetDateTime (:start-date result)))
    (is (instance? OffsetDateTime (:end-date result)))
    (is (empty? (:metadata result)))))

(deftest record-element->map-with-metadata
  (let [element {:tag :Record
                 :attrs {:type "HKQuantityTypeIdentifierHeartRate"
                         :sourceName "Apple Watch"
                         :sourceVersion "10.0"
                         :unit "count/min"
                         :value "72"
                         :device "<<HKDevice>>"
                         :creationDate "2025-01-15 10:30:00 -0300"
                         :startDate "2025-01-15 10:30:00 -0300"
                         :endDate "2025-01-15 10:30:00 -0300"}
                 :content [{:tag :MetadataEntry
                            :attrs {:key "HKMetadataKeyHeartRateMotionContext" :value "1"}}
                           {:tag :MetadataEntry
                            :attrs {:key "HKMetadataKeyHeartRateSensorLocation" :value "2"}}]}
        result (t/record-element->map element)]
    (is (= 2 (count (:metadata result))))
    (is (= "HKMetadataKeyHeartRateMotionContext" (:key (first (:metadata result)))))
    (is (= "1" (:value (first (:metadata result)))))
    (is (= "HKMetadataKeyHeartRateSensorLocation" (:key (second (:metadata result)))))))

;; --- workout-element->map ---

(deftest workout-element->map-full
  (let [element {:tag :Workout
                 :attrs {:workoutActivityType "HKWorkoutActivityTypeRunning"
                         :duration "30.5"
                         :durationUnit "min"
                         :totalDistance "5.2"
                         :totalDistanceUnit "km"
                         :totalEnergyBurned "350.7"
                         :totalEnergyBurnedUnit "kcal"
                         :sourceName "Apple Watch"
                         :sourceVersion "10.0"
                         :device "<<HKDevice>>"
                         :creationDate "2025-01-15 07:00:00 -0300"
                         :startDate "2025-01-15 07:00:00 -0300"
                         :endDate "2025-01-15 07:30:30 -0300"}
                 :content [{:tag :MetadataEntry
                            :attrs {:key "HKIndoorWorkout" :value "0"}}
                           {:tag :WorkoutEvent
                            :attrs {:type "HKWorkoutEventTypePause"
                                    :date "2025-01-15 07:15:00 -0300"
                                    :duration "1.5"
                                    :durationUnit "min"}}
                           {:tag :WorkoutStatistics
                            :attrs {:type "HKQuantityTypeIdentifierHeartRate"
                                    :startDate "2025-01-15 07:00:00 -0300"
                                    :endDate "2025-01-15 07:30:30 -0300"
                                    :average "155.2"
                                    :minimum "120.0"
                                    :maximum "185.5"
                                    :sum ""
                                    :unit "count/min"}}
                           {:tag :WorkoutRoute
                            :attrs {:sourceName "Apple Watch"
                                    :startDate "2025-01-15 07:00:00 -0300"
                                    :endDate "2025-01-15 07:30:30 -0300"}
                            :content [{:tag :FileReference
                                       :attrs {:path "/workout-routes/route.gpx"}}]}]}
        result (t/workout-element->map element)]
    ;; Main attributes
    (is (= "HKWorkoutActivityTypeRunning" (:activity-type result)))
    (is (= 30.5 (:duration result)))
    (is (= "min" (:duration-unit result)))
    (is (= 5.2 (:total-distance result)))
    (is (= "km" (:total-distance-unit result)))
    (is (= 350.7 (:total-energy-burned result)))
    (is (= "kcal" (:total-energy-burned-unit result)))
    (is (instance? OffsetDateTime (:creation-date result)))
    ;; Metadata
    (is (= 1 (count (:metadata result))))
    (is (= "HKIndoorWorkout" (:key (first (:metadata result)))))
    ;; Events
    (is (= 1 (count (:events result))))
    (is (= "HKWorkoutEventTypePause" (:type (first (:events result)))))
    (is (= 1.5 (:duration (first (:events result)))))
    ;; Statistics
    (is (= 1 (count (:statistics result))))
    (let [stat (first (:statistics result))]
      (is (= 155.2 (:average stat)))
      (is (= 120.0 (:minimum stat)))
      (is (= 185.5 (:maximum stat)))
      (is (nil? (:sum stat)))  ;; empty string → nil
      (is (= "count/min" (:unit stat))))
    ;; Routes
    (is (= 1 (count (:routes result))))
    (is (= "/workout-routes/route.gpx" (:file-path (first (:routes result)))))))

(deftest workout-element->map-empty-children
  (let [element {:tag :Workout
                 :attrs {:workoutActivityType "HKWorkoutActivityTypeYoga"
                         :duration "60.0"
                         :durationUnit "min"
                         :sourceName "iPhone"
                         :creationDate "2025-01-15 18:00:00 -0300"
                         :startDate "2025-01-15 18:00:00 -0300"
                         :endDate "2025-01-15 19:00:00 -0300"}
                 :content []}
        result (t/workout-element->map element)]
    (is (= "HKWorkoutActivityTypeYoga" (:activity-type result)))
    (is (empty? (:metadata result)))
    (is (empty? (:events result)))
    (is (empty? (:statistics result)))
    (is (empty? (:routes result)))))

;; --- correlation-element->map ---

(deftest correlation-element->map-test
  (let [element {:tag :Correlation
                 :attrs {:type "HKCorrelationTypeIdentifierBloodPressure"
                         :sourceName "Omron"
                         :sourceVersion "3.0"
                         :device "<<HKDevice>>"
                         :creationDate "2025-01-15 08:00:00 -0300"
                         :startDate "2025-01-15 08:00:00 -0300"
                         :endDate "2025-01-15 08:00:00 -0300"}
                 :content [{:tag :MetadataEntry
                            :attrs {:key "HKMetadataKeyWasUserEntered" :value "1"}}
                           {:tag :Record
                            :attrs {:type "HKQuantityTypeIdentifierBloodPressureSystolic"
                                    :sourceName "Omron"
                                    :sourceVersion "3.0"
                                    :unit "mmHg"
                                    :value "120"
                                    :device "<<HKDevice>>"
                                    :creationDate "2025-01-15 08:00:00 -0300"
                                    :startDate "2025-01-15 08:00:00 -0300"
                                    :endDate "2025-01-15 08:00:00 -0300"}
                            :content []}
                           {:tag :Record
                            :attrs {:type "HKQuantityTypeIdentifierBloodPressureDiastolic"
                                    :sourceName "Omron"
                                    :sourceVersion "3.0"
                                    :unit "mmHg"
                                    :value "80"
                                    :device "<<HKDevice>>"
                                    :creationDate "2025-01-15 08:00:00 -0300"
                                    :startDate "2025-01-15 08:00:00 -0300"
                                    :endDate "2025-01-15 08:00:00 -0300"}
                            :content []}]}
        result (t/correlation-element->map element)]
    (is (= "HKCorrelationTypeIdentifierBloodPressure" (:type result)))
    (is (= "Omron" (:source-name result)))
    (is (= 1 (count (:metadata result))))
    (is (= 2 (count (:records result))))
    (is (= "120" (:value (first (:records result)))))
    (is (= "80" (:value (second (:records result)))))))

;; --- activity-summary-element->map ---

(deftest activity-summary-element->map-test
  (let [element {:tag :ActivitySummary
                 :attrs {:dateComponents "2025-01-15"
                         :activeEnergyBurned "450.5"
                         :activeEnergyBurnedGoal "500"
                         :activeEnergyBurnedUnit "kcal"
                         :appleMoveTime "35.2"
                         :appleMoveTimeGoal "30"
                         :appleExerciseTime "42.0"
                         :appleExerciseTimeGoal "30"
                         :appleStandHours "10"
                         :appleStandHoursGoal "12"}}
        result (t/activity-summary-element->map element)]
    (is (instance? LocalDate (:date-components result)))
    (is (= 450.5 (:active-energy-burned result)))
    (is (= 500.0 (:active-energy-burned-goal result)))
    (is (= "kcal" (:active-energy-burned-unit result)))
    (is (= 35.2 (:apple-move-time result)))
    (is (= 30.0 (:apple-move-time-goal result)))
    (is (= 42.0 (:apple-exercise-time result)))
    (is (= 30.0 (:apple-exercise-time-goal result)))
    (is (= 10.0 (:apple-stand-hours result)))
    (is (= 12.0 (:apple-stand-hours-goal result)))))

(deftest activity-summary-partial
  (let [element {:tag :ActivitySummary
                 :attrs {:dateComponents "2025-01-15"
                         :activeEnergyBurned "100"
                         :activeEnergyBurnedUnit "kcal"}}
        result (t/activity-summary-element->map element)]
    (is (instance? LocalDate (:date-components result)))
    (is (= 100.0 (:active-energy-burned result)))
    (is (nil? (:active-energy-burned-goal result)))
    (is (nil? (:apple-move-time result)))
    (is (nil? (:apple-exercise-time result)))
    (is (nil? (:apple-stand-hours result)))))
