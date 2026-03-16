(ns pulso.test-helpers
  (:require [next.jdbc :as jdbc]
            [pulso.db :as db]
            [pulso.config :as config]
            [pulso.loader.lookups :as lookups]
            [pulso.loader.profile :as profile]))

(def test-db-spec
  "Database spec for pulso_test database."
  (assoc (config/db-spec)
    :dbname (or (System/getenv "TEST_DB_NAME") "pulso_test")))

(defonce ^{:doc "Lazy-initialized datasource for test database."}
  test-ds (delay (db/datasource test-db-spec)))

(defn with-db-once
  "Once fixture: ensures datasource is created and migrations run."
  [f]
  @test-ds  ; Force datasource initialization
  (db/migrate! @test-ds)
  (f))

(defn with-db
  "Each fixture: truncates all tables and resets all caches between tests."
  [f]
  (db/truncate-all! @test-ds)
  (lookups/reset-caches!)
  (profile/reset-state!)
  (f))

(defn count-rows
  "Returns the number of rows in the given table."
  [ds table]
  (:count (jdbc/execute-one! ds [(str "SELECT COUNT(*) AS count FROM " (name table))])))

(defn select-all
  "Returns all rows from the given table."
  [ds table]
  (jdbc/execute! ds [(str "SELECT * FROM " (name table))]))
