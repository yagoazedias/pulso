(ns pulso.config)

(defn db-spec
  "Returns database connection spec, with environment variable overrides."
  []
  {:dbtype   "postgresql"
   :dbname   (or (System/getenv "DB_NAME") "pulso")
   :host     (or (System/getenv "DB_HOST") "localhost")
   :port     (parse-long (or (System/getenv "DB_PORT") "5432"))
   :user     (or (System/getenv "DB_USER") "postgres")
   :password (or (System/getenv "DB_PASSWORD") "postgres")})

(def default-batch-size 5000)
