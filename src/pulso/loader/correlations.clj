(ns pulso.loader.correlations
  (:require [pulso.xml.transform :as transform]
            [pulso.loader.lookups :as lookups]
            [pulso.loader.batch :as batch]))

(defn make-batchers
  "Creates batchers for correlation parent and child tables.
   Uses a dedicated nested-record returning-batcher to avoid
   cross-batcher complexity with top-level records."
  [ds batch-size]
  {:correlation     (batch/make-returning-batcher ds "correlation"
                      [:type :source_id :device_id
                       :creation_date :start_date :end_date]
                      batch-size)
   :metadata        (batch/make-batcher ds "correlation_metadata"
                      [:correlation_id :key :value] batch-size)
   :nested-record   (batch/make-returning-batcher ds "record"
                      [:record_type_id :source_id :device_id :unit_id
                       :value :creation_date :start_date :end_date]
                      batch-size)
   :record-metadata (batch/make-batcher ds "record_metadata"
                      [:record_id :key :value] batch-size)
   :record-link     (batch/make-batcher ds "correlation_record"
                      [:correlation_id :record_id] batch-size)})

(defn- process-returned-nested-records!
  "Takes returned id+metadata pairs from nested-record returning-batcher
   and feeds record metadata and correlation_record links into their batchers."
  [batchers pairs]
  (when pairs
    (doseq [{:keys [id metadata]} pairs]
      (let [{:keys [corr-id record-metadata]} metadata]
        ;; Link correlation <-> record
        ((:add! (:record-link batchers)) [corr-id id])
        ;; Record metadata
        (doseq [m record-metadata]
          ((:add! (:record-metadata batchers)) [id (:key m) (:value m)]))))))

(defn- process-returned-correlations!
  "Takes returned id+children pairs from correlation returning-batcher
   and feeds metadata and nested records into their batchers."
  [ds batchers pairs]
  (when pairs
    (doseq [{:keys [id metadata]} pairs]
      (let [{:keys [corr-metadata records]} metadata]
        ;; Correlation metadata
        (doseq [m corr-metadata]
          ((:add! (:metadata batchers)) [id (:key m) (:value m)]))
        ;; Nested records: pre-resolved FKs are already in the record maps
        (doseq [record-map records]
          (let [record-type-id (lookups/ensure-record-type-id! ds (:type record-map))
                source-id      (lookups/ensure-source-id! ds (:source-name record-map) (:source-version record-map))
                device-id      (lookups/ensure-device-id! ds (:device record-map))
                unit-id        (lookups/ensure-unit-id! ds (:unit record-map))
                row            [record-type-id source-id device-id unit-id
                                (:value record-map) (:creation-date record-map)
                                (:start-date record-map) (:end-date record-map)]
                nested-pairs   ((:add! (:nested-record batchers))
                                row {:corr-id id :record-metadata (:metadata record-map)})]
            (process-returned-nested-records! batchers nested-pairs)))))))

(defn process!
  "Transforms a Correlation element and batches it with nested records."
  [ds batchers element]
  (let [data       (transform/correlation-element->map element)
        source-id  (lookups/ensure-source-id! ds (:source-name data) (:source-version data))
        device-id  (lookups/ensure-device-id! ds (:device data))
        row        [(:type data) source-id device-id
                    (:creation-date data) (:start-date data) (:end-date data)]
        children   {:corr-metadata (:metadata data)
                    :records       (:records data)}
        pairs      ((:add! (:correlation batchers)) row children)]
    (process-returned-correlations! ds batchers pairs)))

(defn flush!
  "Two-phase flush: correlation parents → nested records → metadata + links.
   No longer needs record-batchers parameter."
  [ds batchers]
  ;; Phase 1: flush correlation parents, process children
  (let [corr-pairs ((:flush! (:correlation batchers)))]
    (process-returned-correlations! ds batchers corr-pairs))
  ;; Phase 2: flush nested records, process their metadata + links
  (let [nested-pairs ((:flush! (:nested-record batchers)))]
    (process-returned-nested-records! batchers nested-pairs))
  ;; Phase 3: flush all leaf batchers
  ((:flush! (:metadata batchers)))
  ((:flush! (:record-metadata batchers)))
  ((:flush! (:record-link batchers))))
