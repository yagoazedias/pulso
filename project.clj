(defproject pulso "0.1.0-SNAPSHOT"
  :description "Apple Health XML data ETL into PostgreSQL"
  :license {:name "MIT"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/data.xml "0.2.0-alpha9"]
                 [org.clojure/tools.cli "1.1.230"]
                 [org.clojure/tools.logging "1.3.0"]
                 [com.github.seancorfield/next.jdbc "1.3.939"]
                 [org.postgresql/postgresql "42.7.4"]
                 [com.zaxxer/HikariCP "6.2.1" :exclusions [org.slf4j/slf4j-api]]
                 [migratus "1.5.8"]
                 [org.slf4j/slf4j-api "2.0.15"]
                 [ch.qos.logback/logback-classic "1.5.12"]]
  :main pulso.core
  :aot [pulso.core]
  :plugins [[migratus-lein "0.7.3"]]
  :migratus {:store :database
             :migration-dir "migrations"
             :db {:dbtype "postgresql"
                  :dbname "pulso"
                  :host "localhost"
                  :port 5432
                  :user "postgres"
                  :password "postgres"}}
  :jvm-opts ["-Xmx512m" "-XX:+UseG1GC"]
  :profiles {:uberjar {:aot :all
                        :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
