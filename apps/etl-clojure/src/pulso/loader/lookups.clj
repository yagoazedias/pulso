(ns pulso.loader.lookups
  (:require [next.jdbc :as jdbc]))

(def ^:private source-cache (atom {}))
(def ^:private device-cache (atom {}))
(def ^:private record-type-cache (atom {}))
(def ^:private unit-cache (atom {}))

(defn reset-caches!
  "Clears all lookup caches."
  []
  (reset! source-cache {})
  (reset! device-cache {})
  (reset! record-type-cache {})
  (reset! unit-cache {}))

(defn ensure-source-id!
  "Returns the source ID for the given name+version, inserting if needed."
  [ds source-name source-version]
  (when source-name
    (let [cache-key [source-name source-version]]
      (or (get @source-cache cache-key)
          (let [row (jdbc/execute-one! ds
                      ["INSERT INTO source (name, version) VALUES (?, ?)
                        ON CONFLICT (name, version) DO UPDATE SET name = EXCLUDED.name
                        RETURNING id"
                       source-name source-version])]
            (swap! source-cache assoc cache-key (:source/id row))
            (:source/id row))))))

(defn ensure-device-id!
  "Returns the device ID for the given raw text, inserting if needed."
  [ds raw-text]
  (when raw-text
    (or (get @device-cache raw-text)
        (let [row (jdbc/execute-one! ds
                    ["INSERT INTO device (raw_text) VALUES (?)
                      ON CONFLICT (raw_text) DO UPDATE SET raw_text = EXCLUDED.raw_text
                      RETURNING id"
                     raw-text])]
          (swap! device-cache assoc raw-text (:device/id row))
          (:device/id row)))))

(defn ensure-record-type-id!
  "Returns the record_type ID for the given identifier, inserting if needed."
  [ds identifier]
  (when identifier
    (or (get @record-type-cache identifier)
        (let [row (jdbc/execute-one! ds
                    ["INSERT INTO record_type (identifier) VALUES (?)
                      ON CONFLICT (identifier) DO UPDATE SET identifier = EXCLUDED.identifier
                      RETURNING id"
                     identifier])]
          (swap! record-type-cache assoc identifier (:record_type/id row))
          (:record_type/id row)))))

(defn ensure-unit-id!
  "Returns the unit ID for the given name, inserting if needed."
  [ds unit-name]
  (when unit-name
    (or (get @unit-cache unit-name)
        (let [row (jdbc/execute-one! ds
                    ["INSERT INTO unit (name) VALUES (?)
                      ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                      RETURNING id"
                     unit-name])]
          (swap! unit-cache assoc unit-name (:unit/id row))
          (:unit/id row)))))
