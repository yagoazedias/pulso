(ns pulso.loader.records
  (:require [next.jdbc :as jdbc]
            [pulso.xml.transform :as transform]
            [pulso.loader.batch :as batch]
            [pulso.loader.lookups :as lookups]))

(defn make-batchers
  "Creates record and metadata batchers."
  [ds batch-size]
  {:record   (batch/make-batcher ds "record"
               [:record_type_id :source_id :device_id :unit_id
                :value :creation_date :start_date :end_date]
               batch-size)
   :metadata (batch/make-batcher ds "record_metadata"
               [:record_id :key :value]
               batch-size)})

(defn- insert-record-with-metadata!
  "Inserts a single record with RETURNING id, then batches its metadata."
  [ds metadata-batcher record-type-id source-id device-id unit-id data]
  (let [row (jdbc/execute-one! ds
              ["INSERT INTO record (record_type_id, source_id, device_id, unit_id,
                                    value, creation_date, start_date, end_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id"
               record-type-id source-id device-id unit-id
               (:value data) (:creation-date data)
               (:start-date data) (:end-date data)])
        record-id (:record/id row)]
    (doseq [m (:metadata data)]
      ((:add! metadata-batcher) [record-id (:key m) (:value m)]))))

(defn process!
  "Transforms a Record element and inserts it.
   Records with metadata use individual inserts (need RETURNING id).
   Records without metadata use batch inserts for performance."
  [ds batchers element]
  (let [data           (transform/record-element->map element)
        record-type-id (lookups/ensure-record-type-id! ds (:type data))
        source-id      (lookups/ensure-source-id! ds (:source-name data) (:source-version data))
        device-id      (lookups/ensure-device-id! ds (:device data))
        unit-id        (lookups/ensure-unit-id! ds (:unit data))]
    (if (seq (:metadata data))
      ;; Has metadata: individual insert to get the ID back
      (insert-record-with-metadata! ds (:metadata batchers)
        record-type-id source-id device-id unit-id data)
      ;; No metadata: batch insert
      ((:add! (:record batchers))
       [record-type-id source-id device-id unit-id
        (:value data) (:creation-date data)
        (:start-date data) (:end-date data)]))
    data))

(defn flush!
  "Flushes remaining records and their metadata."
  [batchers]
  ((:flush! (:record batchers)))
  ((:flush! (:metadata batchers))))
