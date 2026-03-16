(ns pulso.loader.workouts
  (:require [pulso.xml.transform :as transform]
            [pulso.loader.lookups :as lookups]
            [pulso.loader.batch :as batch]))

(defn make-batchers
  "Creates batchers for workout parent and child tables."
  [ds batch-size]
  {:workout    (batch/make-returning-batcher ds "workout"
                 [:activity_type :duration :duration_unit
                  :total_distance :total_distance_unit
                  :total_energy_burned :total_energy_burned_unit
                  :source_id :device_id
                  :creation_date :start_date :end_date]
                 batch-size)
   :metadata   (batch/make-batcher ds "workout_metadata"
                 [:workout_id :key :value] batch-size)
   :event      (batch/make-batcher ds "workout_event"
                 [:workout_id :type :date :duration :duration_unit] batch-size)
   :statistics (batch/make-batcher ds "workout_statistics"
                 [:workout_id :type :start_date :end_date
                  :average :minimum :maximum :sum :unit] batch-size)
   :route      (batch/make-batcher ds "workout_route"
                 [:workout_id :source_name :start_date :end_date :file_path] batch-size)})

(defn- process-returned-children!
  "Takes returned id+children pairs from the returning-batcher and feeds
   each child entry into the appropriate batcher."
  [batchers pairs]
  (when pairs
    (doseq [{:keys [id metadata]} pairs]
      (let [{:keys [metadata-entries events statistics routes]} metadata]
        (doseq [m metadata-entries]
          ((:add! (:metadata batchers)) [id (:key m) (:value m)]))
        (doseq [e events]
          ((:add! (:event batchers))
           [id (:type e) (:date e) (:duration e) (:duration-unit e)]))
        (doseq [s statistics]
          ((:add! (:statistics batchers))
           [id (:type s) (:start-date s) (:end-date s)
            (:average s) (:minimum s) (:maximum s) (:sum s) (:unit s)]))
        (doseq [r routes]
          ((:add! (:route batchers))
           [id (:source-name r) (:start-date r) (:end-date r) (:file-path r)]))))))

(defn process!
  "Transforms a Workout element and batches it with all children."
  [ds batchers element]
  (let [data       (transform/workout-element->map element)
        source-id  (lookups/ensure-source-id! ds (:source-name data) (:source-version data))
        device-id  (lookups/ensure-device-id! ds (:device data))
        row        [(:activity-type data)
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
        children   {:metadata-entries (:metadata data)
                    :events           (:events data)
                    :statistics       (:statistics data)
                    :routes           (:routes data)}
        pairs      ((:add! (:workout batchers)) row children)]
    (process-returned-children! batchers pairs)))

(defn flush!
  "Flushes workout parent batcher first, processes children, then flushes all child batchers."
  [batchers]
  (let [pairs ((:flush! (:workout batchers)))]
    (process-returned-children! batchers pairs))
  ((:flush! (:metadata batchers)))
  ((:flush! (:event batchers)))
  ((:flush! (:statistics batchers)))
  ((:flush! (:route batchers))))
