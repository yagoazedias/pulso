(ns pulso.loader.correlations
  (:require [next.jdbc :as jdbc]
            [pulso.xml.transform :as transform]
            [pulso.loader.lookups :as lookups]
            [pulso.loader.batch :as batch]))

(defn make-batchers
  "Creates batchers for correlation child tables."
  [ds batch-size]
  {:metadata (batch/make-batcher ds "correlation_metadata"
               [:correlation_id :key :value] batch-size)
   :record   (batch/make-batcher ds "correlation_record"
               [:correlation_id :record_id] batch-size)})

(defn process!
  "Transforms a Correlation element and inserts it with nested records."
  [ds batchers record-batchers element]
  (let [data          (transform/correlation-element->map element)
        source-id     (lookups/ensure-source-id! ds (:source-name data) (:source-version data))
        device-id     (lookups/ensure-device-id! ds (:device data))
        corr-row      (jdbc/execute-one! ds
                        ["INSERT INTO correlation (type, source_id, device_id,
                                                   creation_date, start_date, end_date)
                          VALUES (?, ?, ?, ?, ?, ?)
                          RETURNING id"
                         (:type data) source-id device-id
                         (:creation-date data) (:start-date data) (:end-date data)]
                        {:return-keys true})
        corr-id       (:correlation/id corr-row)]

    ;; Metadata
    (doseq [m (:metadata data)]
      ((:add! (:metadata batchers)) [corr-id (:key m) (:value m)]))

    ;; Nested records: insert each as a top-level record, then link
    (doseq [record-map (:records data)]
      (let [record-type-id (lookups/ensure-record-type-id! ds (:type record-map))
            source-id      (lookups/ensure-source-id! ds (:source-name record-map) (:source-version record-map))
            device-id      (lookups/ensure-device-id! ds (:device record-map))
            unit-id        (lookups/ensure-unit-id! ds (:unit record-map))
            record-row     (jdbc/execute-one! ds
                             ["INSERT INTO record (record_type_id, source_id, device_id, unit_id,
                                                   value, creation_date, start_date, end_date)
                               VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                               RETURNING id"
                              record-type-id source-id device-id unit-id
                              (:value record-map) (:creation-date record-map)
                              (:start-date record-map) (:end-date record-map)]
)
            record-id      (:record/id record-row)]
        ;; Record metadata
        (doseq [m (:metadata record-map)]
          ((:add! (:metadata record-batchers)) [record-id (:key m) (:value m)]))
        ;; Link correlation <-> record
        ((:add! (:record batchers)) [corr-id record-id])))

    corr-id))

(defn flush!
  "Flushes all correlation batchers."
  [batchers]
  ((:flush! (:metadata batchers)))
  ((:flush! (:record batchers))))
