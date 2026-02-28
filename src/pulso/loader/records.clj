(ns pulso.loader.records
  (:require [pulso.xml.transform :as transform]
            [pulso.loader.batch :as batch]
            [pulso.loader.lookups :as lookups]))

(defn make-batchers
  "Creates record and metadata batchers."
  [ds batch-size]
  {:record          (batch/make-batcher ds "record"
                      [:record_type_id :source_id :device_id :unit_id
                       :value :creation_date :start_date :end_date]
                      batch-size)
   :record-with-meta (batch/make-returning-batcher ds "record"
                       [:record_type_id :source_id :device_id :unit_id
                        :value :creation_date :start_date :end_date]
                       batch-size)
   :metadata        (batch/make-batcher ds "record_metadata"
                      [:record_id :key :value]
                      batch-size)})

(defn- process-returned-metadata!
  "Takes returned id+metadata pairs from the returning-batcher and feeds
   each metadata entry into the metadata batcher."
  [metadata-batcher pairs]
  (when pairs
    (doseq [{:keys [id metadata]} pairs
            m metadata]
      ((:add! metadata-batcher) [id (:key m) (:value m)]))))

(defn process!
  "Transforms a Record element and inserts it.
   All records use batch inserts. Records with metadata use a
   returning-batcher so IDs can be matched with their metadata."
  [ds batchers element]
  (let [data           (transform/record-element->map element)
        record-type-id (lookups/ensure-record-type-id! ds (:type data))
        source-id      (lookups/ensure-source-id! ds (:source-name data) (:source-version data))
        device-id      (lookups/ensure-device-id! ds (:device data))
        unit-id        (lookups/ensure-unit-id! ds (:unit data))
        row            [record-type-id source-id device-id unit-id
                        (:value data) (:creation-date data)
                        (:start-date data) (:end-date data)]]
    (if (seq (:metadata data))
      ;; Has metadata: use returning-batcher to get IDs back in batches
      (let [pairs ((:add! (:record-with-meta batchers)) row (:metadata data))]
        (process-returned-metadata! (:metadata batchers) pairs))
      ;; No metadata: simple batch insert
      ((:add! (:record batchers)) row))
    data))

(defn flush!
  "Flushes remaining records and their metadata.
   Returning-batcher must flush first so IDs are available for metadata."
  [batchers]
  (let [pairs ((:flush! (:record-with-meta batchers)))]
    (process-returned-metadata! (:metadata batchers) pairs))
  ((:flush! (:record batchers)))
  ((:flush! (:metadata batchers))))
