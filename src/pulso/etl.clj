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
  "Runs the full ETL pipeline: parse XML → transform → load into PostgreSQL.

   Accepts an optional opts map:
     :on-element - callback fn called with element keyword after each element is processed.
                   When provided, the old log-every-100k progress reporting is disabled."
  ([ds xml-file batch-size]
   (execute! ds xml-file batch-size {}))
  ([ds xml-file batch-size opts]
   (log/info "Starting ETL pipeline" {:file xml-file :batch-size batch-size})
   (let [start        (System/currentTimeMillis)
         on-element   (:on-element opts)
         use-log-progress (nil? on-element)
         progress-every 100000]

     ;; Prepare: truncate for idempotency, reset caches
     (db/truncate-all! ds)
     (lookups/reset-caches!)
     (profile/reset-state!)

     ;; Initialize batchers
     (let [record-batchers  (records/make-batchers ds batch-size)
           workout-batchers (workouts/make-batchers ds batch-size)
           corr-batchers    (correlations/make-batchers ds batch-size)
           activity-batcher (activity/make-batcher ds batch-size)
           counters         (atom {:records 0 :workouts 0 :correlations 0 :activities 0})]

       ;; Stream and process
       (parser/parse-health-data xml-file
         (fn [element _locale]
           (case (:tag element)
             :ExportDate
             (do (profile/save-export-date! element)
                 (when on-element (on-element :ExportDate)))

             :Me
             (do (profile/save-profile! ds element _locale)
                 (when on-element (on-element :Me)))

             :Record
             (do (records/process! ds record-batchers element)
                 (let [n (swap! counters update :records inc)]
                   (when (and use-log-progress
                              (zero? (mod (:records n) progress-every)))
                     (log/info "Progress:" (:records n) "records processed")))
                 (when on-element (on-element :Record)))

             :Workout
             (do (workouts/process! ds workout-batchers element)
                 (swap! counters update :workouts inc)
                 (when on-element (on-element :Workout)))

             :Correlation
             (do (correlations/process! ds corr-batchers element)
                 (swap! counters update :correlations inc)
                 (when on-element (on-element :Correlation)))

             :ActivitySummary
             (do (activity/process! activity-batcher element)
                 (swap! counters update :activities inc)
                 (when on-element (on-element :ActivitySummary)))

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
         counts)))))
