(ns pulso.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [migratus.core :as migratus]
            [clojure.tools.logging :as log])
  (:import [com.zaxxer.hikari HikariDataSource]))

(defn datasource
  "Creates a HikariCP connection pool datasource."
  [db-spec]
  (let [jdbc-url (format "jdbc:postgresql://%s:%s/%s"
                         (:host db-spec) (:port db-spec) (:dbname db-spec))]
    (connection/->pool HikariDataSource
                       {:jdbcUrl          jdbc-url
                        :username         (:user db-spec)
                        :password         (:password db-spec)
                        :maximumPoolSize  4})))

(defn migratus-config
  "Returns migratus configuration for the given datasource."
  [ds]
  {:store         :database
   :migration-dir "migrations"
   :db            {:datasource ds}})

(defn migrate!
  "Runs pending database migrations."
  [ds]
  (log/info "Running database migrations...")
  (migratus/migrate (migratus-config ds))
  (log/info "Migrations complete."))

(defn truncate-all!
  "Truncates all tables for idempotent re-import."
  [ds]
  (log/info "Truncating all tables...")
  (jdbc/execute! ds ["TRUNCATE
                      correlation_record, correlation_metadata, correlation,
                      workout_route, workout_statistics, workout_event, workout_metadata, workout,
                      record_metadata, record,
                      activity_summary, user_profile,
                      source, device, record_type, unit
                      CASCADE"])
  (log/info "All tables truncated."))
