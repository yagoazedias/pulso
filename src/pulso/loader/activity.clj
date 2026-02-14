(ns pulso.loader.activity
  (:require [pulso.xml.transform :as transform]
            [pulso.loader.batch :as batch]))

(defn make-batcher [ds batch-size]
  (batch/make-batcher ds "activity_summary"
                      [:date_components
                       :active_energy_burned :active_energy_burned_goal
                       :active_energy_burned_unit
                       :apple_move_time :apple_move_time_goal
                       :apple_exercise_time :apple_exercise_time_goal
                       :apple_stand_hours :apple_stand_hours_goal]
                      batch-size))

(defn process!
  "Transforms an ActivitySummary element and adds it to the batch."
  [batcher element]
  (let [data (transform/activity-summary-element->map element)]
    ((:add! batcher)
     [(:date-components data)
      (:active-energy-burned data) (:active-energy-burned-goal data)
      (:active-energy-burned-unit data)
      (:apple-move-time data) (:apple-move-time-goal data)
      (:apple-exercise-time data) (:apple-exercise-time-goal data)
      (:apple-stand-hours data) (:apple-stand-hours-goal data)])))
