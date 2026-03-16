(ns pulso.loader.profile
  (:require [next.jdbc :as jdbc]
            [pulso.xml.transform :as transform]
            [clojure.tools.logging :as log]))

(def ^:private export-date (atom nil))

(defn reset-state! []
  (reset! export-date nil))

(defn save-export-date!
  "Stores the export date for later use when saving the profile."
  [element]
  (let [data (transform/export-date-element->map element)]
    (reset! export-date (:export-date data))
    (log/info "Export date:" (:export-date data))))

(defn save-profile!
  "Inserts the user profile into the database."
  [ds element locale]
  (let [data (transform/me-element->map element locale)]
    (log/info "Saving user profile - DOB:" (:date-of-birth data)
              "Sex:" (:biological-sex data))
    (jdbc/execute-one! ds
      ["INSERT INTO user_profile (date_of_birth, biological_sex, blood_type,
                                  fitzpatrick_skin, cardio_fitness_meds,
                                  export_date, locale)
        VALUES (?, ?, ?, ?, ?, ?, ?)"
       (:date-of-birth data)
       (:biological-sex data)
       (:blood-type data)
       (:fitzpatrick-skin data)
       (:cardio-fitness-meds data)
       @export-date
       (:locale data)])))
