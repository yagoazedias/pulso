(ns pulso.loader.workouts
  (:require [next.jdbc :as jdbc]
            [pulso.xml.transform :as transform]
            [pulso.loader.lookups :as lookups]
            [pulso.loader.batch :as batch]))

(defn make-batchers
  "Creates batchers for workout child tables."
  [ds batch-size]
  {:metadata   (batch/make-batcher ds "workout_metadata"
                 [:workout_id :key :value] batch-size)
   :event      (batch/make-batcher ds "workout_event"
                 [:workout_id :type :date :duration :duration_unit] batch-size)
   :statistics (batch/make-batcher ds "workout_statistics"
                 [:workout_id :type :start_date :end_date
                  :average :minimum :maximum :sum :unit] batch-size)
   :route      (batch/make-batcher ds "workout_route"
                 [:workout_id :source_name :start_date :end_date :file_path] batch-size)})

(defn process!
  "Transforms a Workout element and inserts it with all children."
  [ds batchers element]
  (let [data       (transform/workout-element->map element)
        source-id  (lookups/ensure-source-id! ds (:source-name data) (:source-version data))
        device-id  (lookups/ensure-device-id! ds (:device data))
        workout-row (jdbc/execute-one! ds
                      ["INSERT INTO workout (activity_type, duration, duration_unit,
                                             total_distance, total_distance_unit,
                                             total_energy_burned, total_energy_burned_unit,
                                             source_id, device_id,
                                             creation_date, start_date, end_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        RETURNING id"
                       (:activity-type data)
                       (:duration data)
                       (:duration-unit data)
                       (:total-distance data)
                       (:total-distance-unit data)
                       (:total-energy-burned data)
                       (:total-energy-burned-unit data)
                       source-id device-id
                       (:creation-date data)
                       (:start-date data)
                       (:end-date data)]
)
        workout-id (:workout/id workout-row)]

    ;; Metadata
    (doseq [m (:metadata data)]
      ((:add! (:metadata batchers)) [workout-id (:key m) (:value m)]))

    ;; Events
    (doseq [e (:events data)]
      ((:add! (:event batchers))
       [workout-id (:type e) (:date e) (:duration e) (:duration-unit e)]))

    ;; Statistics
    (doseq [s (:statistics data)]
      ((:add! (:statistics batchers))
       [workout-id (:type s) (:start-date s) (:end-date s)
        (:average s) (:minimum s) (:maximum s) (:sum s) (:unit s)]))

    ;; Routes
    (doseq [r (:routes data)]
      ((:add! (:route batchers))
       [workout-id (:source-name r) (:start-date r) (:end-date r) (:file-path r)]))

    workout-id))

(defn flush!
  "Flushes all workout child batchers."
  [batchers]
  ((:flush! (:metadata batchers)))
  ((:flush! (:event batchers)))
  ((:flush! (:statistics batchers)))
  ((:flush! (:route batchers))))
