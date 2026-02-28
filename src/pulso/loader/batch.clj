(ns pulso.loader.batch
  (:require [next.jdbc :as jdbc]
            [next.jdbc.prepare :as prepare]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn- flush-batch!
  "Executes a batch insert and clears the buffer."
  [ds sql buffer-atom]
  (let [rows @buffer-atom]
    (when (seq rows)
      (jdbc/with-transaction [tx ds]
        (with-open [ps (jdbc/prepare tx [sql])]
          (prepare/execute-batch! ps rows)))
      (reset! buffer-atom [])
      nil)))

(defn- flush-batch-returning!
  "Executes a multi-row INSERT...RETURNING id and clears the buffer.
   Returns a seq of result maps with the generated keys."
  [ds table columns buffer-atom]
  (let [rows @buffer-atom]
    (when (seq rows)
      (let [col-names  (str/join ", " (map name columns))
            one-row    (str "(" (str/join ", " (repeat (count columns) "?")) ")")
            all-rows   (str/join ", " (repeat (count rows) one-row))
            sql        (format "INSERT INTO %s (%s) VALUES %s RETURNING id"
                               table col-names all-rows)
            params     (vec (mapcat identity rows))
            result     (jdbc/with-transaction [tx ds]
                         (jdbc/execute! tx (into [sql] params)))]
        (reset! buffer-atom [])
        result))))

(defn make-batcher
  "Creates a stateful batcher for batch inserts.

   Returns a map with:
     :add!   - fn [row-vec] adds a row, auto-flushes at batch-size
     :flush! - fn [] flushes remaining rows
     :count  - fn [] returns total rows added

   row-vec should be a vector of values matching the columns order."
  [ds table columns batch-size]
  (let [buffer  (atom [])
        total   (atom 0)
        sql     (format "INSERT INTO %s (%s) VALUES (%s)"
                        table
                        (str/join ", " (map name columns))
                        (str/join ", " (repeat (count columns) "?")))]
    {:add!   (fn [row-vec]
               (swap! buffer conj row-vec)
               (swap! total inc)
               (when (>= (count @buffer) batch-size)
                 (flush-batch! ds sql buffer)
                 nil))
     :flush! (fn []
               (flush-batch! ds sql buffer))
     :count  (fn [] @total)}))

(defn make-returning-batcher
  "Like make-batcher but returns generated IDs on flush.

   Returns a map with:
     :add!        - fn [row-vec metadata] adds a row with associated metadata
     :flush!      - fn [] flushes and returns seq of {:id ... :metadata ...}
     :count       - fn [] returns total rows added"
  [ds table columns batch-size]
  (let [rows-buffer    (atom [])
        meta-buffer    (atom [])
        total          (atom 0)]
    {:add!   (fn [row-vec metadata]
               (swap! rows-buffer conj row-vec)
               (swap! meta-buffer conj metadata)
               (swap! total inc)
               (when (>= (count @rows-buffer) batch-size)
                 (let [result   (flush-batch-returning! ds table columns rows-buffer)
                       metas    @meta-buffer
                       paired   (mapv (fn [r m]
                                        {:id       (val (first r))
                                         :metadata m})
                                      result metas)]
                   (reset! meta-buffer [])
                   paired)))
     :flush! (fn []
               (let [result (flush-batch-returning! ds table columns rows-buffer)
                     metas  @meta-buffer
                     paired (when result
                              (mapv (fn [r m]
                                      {:id       (val (first r))
                                       :metadata m})
                                    result metas))]
                 (reset! meta-buffer [])
                 paired))
     :count  (fn [] @total)}))
