(ns pulso.xml.transform
  (:import [java.time OffsetDateTime LocalDate]
           [java.time.format DateTimeFormatter]))

(def ^:private apple-health-datetime-fmt
  (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss Z"))

(defn parse-datetime
  "Parses Apple Health datetime string to OffsetDateTime."
  [s]
  (when (and s (not (empty? s)))
    (OffsetDateTime/parse s apple-health-datetime-fmt)))

(defn parse-date
  "Parses a date string (yyyy-MM-dd) to LocalDate."
  [s]
  (when (and s (not (empty? s)))
    (LocalDate/parse s)))

(defn parse-dbl
  "Safely parses a string to Double, returns nil on failure."
  [s]
  (when (and s (not (empty? s)))
    (try (Double/parseDouble s) (catch NumberFormatException _ nil))))

(defn- extract-metadata
  "Extracts MetadataEntry children from an element."
  [element]
  (->> (:content element)
       (filter #(= (:tag %) :MetadataEntry))
       (mapv (fn [entry]
               {:key   (get-in entry [:attrs :key])
                :value (get-in entry [:attrs :value])}))))

(defn export-date-element->map [element]
  {:export-date (parse-datetime (get-in element [:attrs :value]))})

(defn me-element->map [element locale]
  (let [attrs (:attrs element)]
    {:date-of-birth       (parse-date (:HKCharacteristicTypeIdentifierDateOfBirth attrs))
     :biological-sex      (:HKCharacteristicTypeIdentifierBiologicalSex attrs)
     :blood-type          (:HKCharacteristicTypeIdentifierBloodType attrs)
     :fitzpatrick-skin    (:HKCharacteristicTypeIdentifierFitzpatrickSkinType attrs)
     :cardio-fitness-meds (:HKCharacteristicTypeIdentifierCardioFitnessMedicationsUse attrs)
     :locale              locale}))

(defn record-element->map [element]
  (let [attrs (:attrs element)]
    {:type           (:type attrs)
     :source-name    (:sourceName attrs)
     :source-version (:sourceVersion attrs)
     :unit           (:unit attrs)
     :value          (:value attrs)
     :device         (:device attrs)
     :creation-date  (parse-datetime (:creationDate attrs))
     :start-date     (parse-datetime (:startDate attrs))
     :end-date       (parse-datetime (:endDate attrs))
     :metadata       (extract-metadata element)}))

(defn workout-element->map [element]
  (let [attrs (:attrs element)
        children (:content element)]
    {:activity-type            (:workoutActivityType attrs)
     :duration                 (parse-dbl (:duration attrs))
     :duration-unit            (:durationUnit attrs)
     :total-distance           (parse-dbl (:totalDistance attrs))
     :total-distance-unit      (:totalDistanceUnit attrs)
     :total-energy-burned      (parse-dbl (:totalEnergyBurned attrs))
     :total-energy-burned-unit (:totalEnergyBurnedUnit attrs)
     :source-name              (:sourceName attrs)
     :source-version           (:sourceVersion attrs)
     :device                   (:device attrs)
     :creation-date            (parse-datetime (:creationDate attrs))
     :start-date               (parse-datetime (:startDate attrs))
     :end-date                 (parse-datetime (:endDate attrs))
     :metadata                 (extract-metadata element)
     :events                   (->> children
                                    (filter #(= (:tag %) :WorkoutEvent))
                                    (mapv (fn [e]
                                            (let [a (:attrs e)]
                                              {:type          (:type a)
                                               :date          (parse-datetime (:date a))
                                               :duration      (parse-dbl (:duration a))
                                               :duration-unit (:durationUnit a)}))))
     :statistics               (->> children
                                    (filter #(= (:tag %) :WorkoutStatistics))
                                    (mapv (fn [e]
                                            (let [a (:attrs e)]
                                              {:type       (:type a)
                                               :start-date (parse-datetime (:startDate a))
                                               :end-date   (parse-datetime (:endDate a))
                                               :average    (parse-dbl (:average a))
                                               :minimum    (parse-dbl (:minimum a))
                                               :maximum    (parse-dbl (:maximum a))
                                               :sum        (parse-dbl (:sum a))
                                               :unit       (:unit a)}))))
     :routes                   (->> children
                                    (filter #(= (:tag %) :WorkoutRoute))
                                    (mapv (fn [e]
                                            (let [a (:attrs e)]
                                              {:source-name (:sourceName a)
                                               :start-date  (parse-datetime (:startDate a))
                                               :end-date    (parse-datetime (:endDate a))
                                               :file-path   (->> (:content e)
                                                                  (filter #(= (:tag %) :FileReference))
                                                                  first
                                                                  :attrs
                                                                  :path)}))))}))

(defn correlation-element->map [element]
  (let [attrs (:attrs element)]
    {:type           (:type attrs)
     :source-name    (:sourceName attrs)
     :source-version (:sourceVersion attrs)
     :device         (:device attrs)
     :creation-date  (parse-datetime (:creationDate attrs))
     :start-date     (parse-datetime (:startDate attrs))
     :end-date       (parse-datetime (:endDate attrs))
     :metadata       (extract-metadata element)
     :records        (->> (:content element)
                          (filter #(= (:tag %) :Record))
                          (mapv record-element->map))}))

(defn activity-summary-element->map [element]
  (let [attrs (:attrs element)]
    {:date-components           (parse-date (:dateComponents attrs))
     :active-energy-burned      (parse-dbl (:activeEnergyBurned attrs))
     :active-energy-burned-goal (parse-dbl (:activeEnergyBurnedGoal attrs))
     :active-energy-burned-unit (:activeEnergyBurnedUnit attrs)
     :apple-move-time           (parse-dbl (:appleMoveTime attrs))
     :apple-move-time-goal      (parse-dbl (:appleMoveTimeGoal attrs))
     :apple-exercise-time       (parse-dbl (:appleExerciseTime attrs))
     :apple-exercise-time-goal  (parse-dbl (:appleExerciseTimeGoal attrs))
     :apple-stand-hours         (parse-dbl (:appleStandHours attrs))
     :apple-stand-hours-goal    (parse-dbl (:appleStandHoursGoal attrs))}))
