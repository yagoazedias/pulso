(ns pulso.core
  (:require [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [pulso.config :as config]
            [pulso.db :as db]
            [pulso.etl :as etl])
  (:gen-class))

(def cli-options
  [["-f" "--file FILE" "Path to Apple Health XML export file"
    :validate [#(.exists (java.io.File. %)) "File does not exist"]]
   ["-b" "--batch-size SIZE" "Batch insert size"
    :default config/default-batch-size
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help" "Show help"]])

(defn- usage [summary]
  (str "Pulso - Apple Health XML to PostgreSQL ETL\n\n"
       "Usage: pulso [options]\n\n"
       "Options:\n"
       summary))

(defn -main [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (do (println (usage summary))
          (System/exit 0))

      errors
      (do (doseq [e errors] (println "Error:" e))
          (println (usage summary))
          (System/exit 1))

      (nil? (:file options))
      (do (println "Error: --file is required")
          (println (usage summary))
          (System/exit 1))

      :else
      (let [db-spec (config/db-spec)
            ds      (db/datasource db-spec)]
        (log/info "Connecting to database" (select-keys db-spec [:host :port :dbname]))
        (db/migrate! ds)
        (let [result (etl/execute! ds (:file options) (:batch-size options))]
          (log/info "Final counts:" result)
          (System/exit 0))))))
