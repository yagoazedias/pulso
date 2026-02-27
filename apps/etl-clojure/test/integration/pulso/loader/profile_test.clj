(ns pulso.loader.profile-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [pulso.test-helpers :refer [test-ds with-db-once with-db count-rows select-all]]
            [pulso.loader.profile :as profile]
            [clojure.data.xml :as xml])
  (:import [java.time OffsetDateTime]))

(use-fixtures :once with-db-once)
(use-fixtures :each with-db)

(deftest save-export-date-stores-value
  (testing "Given reset export-date atom and ExportDate XML element"
    (profile/reset-state!)
    (let [element (xml/element :ExportDate {:value "2025-01-15 12:00:00 -0300"})]

      (testing "When save-export-date! is called and then save-profile! is called"
        (profile/save-export-date! element)
        (let [me-element (xml/element :Me
              {:HKCharacteristicTypeIdentifierDateOfBirth "1990-05-15"
               :HKCharacteristicTypeIdentifierBiologicalSex "HKBiologicalSexMale"})]
          (profile/save-profile! @test-ds me-element "pt_BR")))

      (testing "Then user_profile has export_date correctly populated"
        (is (= 1 (count-rows @test-ds "user_profile")))
        (let [rows (select-all @test-ds "user_profile")
              row (first rows)
              export-date (:user_profile/export_date row)
              zoned-date (java.time.ZonedDateTime/ofInstant (.toInstant export-date) (java.time.ZoneId/of "UTC"))]
          (is (= 2025 (.getYear zoned-date)))
          (is (= 1 (.getMonthValue zoned-date)))
          (is (= 15 (.getDayOfMonth zoned-date))))))))

(deftest save-profile-inserts-row
  (testing "Given export date set and Me XML element with profile data"
    (profile/reset-state!)
    (let [export-element (xml/element :ExportDate {:value "2025-01-15 12:00:00 -0300"})
          me-element (xml/element :Me
            {:HKCharacteristicTypeIdentifierDateOfBirth "1990-05-15"
             :HKCharacteristicTypeIdentifierBiologicalSex "HKBiologicalSexMale"})]

      (profile/save-export-date! export-element)

      (testing "When save-profile! is called"
        (profile/save-profile! @test-ds me-element "pt_BR"))

      (testing "Then user_profile table has 1 row with correct DOB, sex, locale"
        (is (= 1 (count-rows @test-ds "user_profile")))
        (let [rows (select-all @test-ds "user_profile")
              row (first rows)]
          (is (= "1990-05-15" (str (:user_profile/date_of_birth row))))
          (is (= "HKBiologicalSexMale" (:user_profile/biological_sex row)))
          (is (= "pt_BR" (:user_profile/locale row))))))))
