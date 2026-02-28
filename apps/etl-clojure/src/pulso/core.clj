(ns pulso.core
  (:require [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [pulso.config :as config]
            [pulso.db :as db]
            [pulso.etl :as etl]
            [pulso.xml.counter :as counter]
            [pulso.progress :as progress]
            [pulso.ui.terminal :as terminal])
  (:gen-class))

(def cli-options
  [["-f" "--file FILE" "Path to Apple Health XML export file"
    :validate [#(.exists (java.io.File. %)) "File does not exist"]]
   ["-b" "--batch-size SIZE" "Batch insert size"
    :default config/default-batch-size
    :parse-fn #(Integer/parseInt %)]
   ["-p" "--[no-]progress" "Show progress bar (default: true)"
    :default true]
   ["-h" "--help" "Show help"]])

(defn- usage [summary]
  (str "Pulso - Apple Health XML to PostgreSQL ETL\n\n"
       "Usage: pulso [options]\n\n"
       "Options:\n"
       summary))

(defn- detach-console-appender!
  "Detaches the STDOUT logback appender to prevent log lines
   from interfering with the progress UI."
  []
  (let [^ch.qos.logback.classic.Logger root
        (cast ch.qos.logback.classic.Logger
              (org.slf4j.LoggerFactory/getLogger
                org.slf4j.Logger/ROOT_LOGGER_NAME))]
    (when-let [appender (.getAppender root "STDOUT")]
      (.detachAppender root "STDOUT")
      appender)))

(defn- reattach-console-appender!
  "Reattaches a previously detached logback appender."
  [appender]
  (when appender
    (let [^ch.qos.logback.classic.Logger root
          (cast ch.qos.logback.classic.Logger
                (org.slf4j.LoggerFactory/getLogger
                  org.slf4j.Logger/ROOT_LOGGER_NAME))]
      (.addAppender root appender))))

(defn- filename-from-path [path]
  (.getName (java.io.File. path)))

(defn- run-with-progress!
  "Runs the ETL with a two-pass pipeline: count elements, then process
   with a rich terminal progress UI."
  [ds xml-file batch-size]
  (println (str "Pulso ETL - Counting elements in " (filename-from-path xml-file) "..."))
  (let [totals     (counter/count-elements xml-file)
        state-atom (progress/make-state totals)
        filename   (filename-from-path xml-file)
        ;; Suppress console logging during progress UI
        appender   (detach-console-appender!)
        renderer   (terminal/start-renderer! state-atom progress/snapshot filename)]
    (try
      (let [result (etl/execute! ds xml-file batch-size
                                 {:on-element (fn [element-type]
                                                (progress/record-progress! state-atom element-type))})]
        ((:stop! renderer))
        (reattach-console-appender! appender)
        (println)
        (log/info "Final counts:" result)
        result)
      (catch Exception e
        ((:stop! renderer))
        (reattach-console-appender! appender)
        (throw e)))))

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
        (if (:progress options)
          (run-with-progress! ds (:file options) (:batch-size options))
          (let [result (etl/execute! ds (:file options) (:batch-size options))]
            (log/info "Final counts:" result)))
        (System/exit 0)))))
