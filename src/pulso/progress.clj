(ns pulso.progress
  "Progress state management for the ETL pipeline.
   Tracks per-element-type counts against known totals
   and derives percentages, throughput, and ETA.")

(def tracked-types
  "Element types shown in the progress UI, in display order."
  [:Record :Workout :Correlation :ActivitySummary])

(defn make-state
  "Creates a progress state atom from a totals map (e.g. from xml.counter).
   Only tracks types listed in `tracked-types`."
  [totals]
  (atom {:totals    (select-keys totals tracked-types)
         :processed (zipmap tracked-types (repeat 0))
         :start-ms  (System/currentTimeMillis)
         :phase     :processing}))

(defn record-progress!
  "Increments the processed count for the given element type."
  [state-atom element-type]
  (when (contains? (set tracked-types) element-type)
    (swap! state-atom update-in [:processed element-type] (fnil inc 0))))

(defn snapshot
  "Pure function: derives display-ready data from a progress state map.
   Returns:
     {:types [{:type :Record :processed 258412 :total 385421 :pct 67.3} ...]
      :overall-processed 258796
      :overall-total     386187
      :overall-pct       67.1
      :rate              12483
      :eta-secs          10
      :elapsed-secs      42}"
  [state]
  (let [{:keys [totals processed start-ms]} state
        now         (System/currentTimeMillis)
        elapsed-ms  (max 1 (- now start-ms))
        elapsed-s   (/ elapsed-ms 1000.0)
        types       (mapv (fn [t]
                            (let [proc (get processed t 0)
                                  tot  (get totals t 0)
                                  pct  (if (pos? tot)
                                         (* 100.0 (/ (double proc) (double tot)))
                                         0.0)]
                              {:type t :processed proc :total tot :pct pct}))
                          tracked-types)
        overall-proc (reduce + (map :processed types))
        overall-tot  (reduce + (map :total types))
        overall-pct  (if (pos? overall-tot)
                       (* 100.0 (/ (double overall-proc) (double overall-tot)))
                       0.0)
        rate         (if (pos? elapsed-s)
                       (long (/ overall-proc elapsed-s))
                       0)
        remaining    (- overall-tot overall-proc)
        eta-secs     (if (pos? rate)
                       (long (/ remaining rate))
                       0)]
    {:types            types
     :overall-processed overall-proc
     :overall-total     overall-tot
     :overall-pct       overall-pct
     :rate              rate
     :eta-secs          eta-secs
     :elapsed-secs      (long elapsed-s)}))
