(ns pulso.etl
  (:require [clojure.tools.logging :as log]
            [pulso.db :as db]
            [pulso.xml.parser :as parser]
            [pulso.loader.profile :as profile]
            [pulso.loader.records :as records]
            [pulso.loader.workouts :as workouts]
            [pulso.loader.correlations :as correlations]
            [pulso.loader.activity :as activity]
            [pulso.loader.lookups :as lookups]))

(defn execute!
  "Runs the full ETL pipeline: parse XML → transform → load into PostgreSQL."
  [ds xml-file batch-size]
  (log/info "Starting ETL pipeline" {:file xml-file :batch-size batch-size})
  (let [start (System/currentTimeMillis)]

    ;; Prepare: truncate for idempotency, reset caches
    (db/truncate-all! ds)
    (lookups/reset-caches!)
    (profile/reset-state!)

    ;; Initialize batchers
    ;; TODO: this should be done in a more functional way, maybe a strategy pattern?
    (let [record-batchers  (records/make-batchers ds batch-size)
          workout-batchers (workouts/make-batchers ds batch-size)
          corr-batchers    (correlations/make-batchers ds batch-size)
          activity-batcher (activity/make-batcher ds batch-size)
          counters         (atom {:records 0 :workouts 0 :correlations 0 :activities 0})
          progress-every   100000]

      ;; Stream and process
      (parser/parse-health-data xml-file
        (fn [element _locale]
          (case (:tag element)
            :ExportDate
            (profile/save-export-date! element)

            :Me
            (profile/save-profile! ds element _locale)

            :Record
            (do (records/process! ds record-batchers element)
                (let [n (swap! counters update :records inc)]
                  (when (zero? (mod (:records n) progress-every))
                    (log/info "Progress:" (:records n) "records processed"))))

            :Workout
            (do (workouts/process! ds workout-batchers element)
                (swap! counters update :workouts inc))

            :Correlation
            (do (correlations/process! ds corr-batchers element)
                (swap! counters update :correlations inc))

            :ActivitySummary
            (do (activity/process! activity-batcher element)
                (swap! counters update :activities inc))

            ;; Skip unknown elements
            (log/debug "Skipping element" {:tag (:tag element)}))))

      ;; Flush remaining partial batches
      (records/flush! record-batchers)
      (workouts/flush! workout-batchers)
      (correlations/flush! ds corr-batchers)
      ((:flush! activity-batcher))

      ;; Report
      (let [elapsed  (- (System/currentTimeMillis) start)
            counts   @counters]
        (log/info "ETL complete!" (assoc counts :elapsed-ms elapsed
                                                :elapsed-min (format "%.1f" (/ elapsed 60000.0))))
        counts))))
