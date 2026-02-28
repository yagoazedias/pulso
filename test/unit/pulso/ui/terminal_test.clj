(ns pulso.ui.terminal-test
  (:require [clojure.test :refer [deftest is testing]]
            [pulso.ui.terminal]))

;; Access private functions via var lookup
(def format-number   #'pulso.ui.terminal/format-number)
(def format-duration #'pulso.ui.terminal/format-duration)
(def progress-bar    #'pulso.ui.terminal/progress-bar)
(def render-type-line #'pulso.ui.terminal/render-type-line)
(def render-frame    #'pulso.ui.terminal/render-frame)

;; --- format-number ---

(deftest format-number-test
  (testing "small numbers pass through unchanged"
    (is (= "0" (format-number 0)))
    (is (= "42" (format-number 42)))
    (is (= "999" (format-number 999))))
  (testing "thousands get comma separators"
    (is (= "1,000" (format-number 1000)))
    (is (= "1,234,567" (format-number 1234567))))
  (testing "millions format correctly"
    (is (= "10,000,000" (format-number 10000000)))))

;; --- format-duration ---

(deftest format-duration-test
  (testing "zero seconds"
    (is (= "0:00" (format-duration 0))))
  (testing "seconds only"
    (is (= "0:05" (format-duration 5)))
    (is (= "0:59" (format-duration 59))))
  (testing "minutes and seconds"
    (is (= "1:00" (format-duration 60)))
    (is (= "1:05" (format-duration 65)))
    (is (= "10:30" (format-duration 630)))))

;; --- progress-bar ---

(deftest progress-bar-test
  (testing "0% is all empty"
    (let [bar (progress-bar 0 10)]
      (is (= "[░░░░░░░░░░]" bar))))
  (testing "100% is all filled"
    (let [bar (progress-bar 100 10)]
      (is (= "[██████████]" bar))))
  (testing "50% is half filled"
    (let [bar (progress-bar 50 10)]
      (is (= "[█████░░░░░]" bar))))
  (testing "does not exceed width at >100%"
    (let [bar (progress-bar 150 10)]
      (is (= "[██████████]" bar)))))

;; --- render-type-line ---

(deftest render-type-line-test
  (testing "renders a complete type progress line"
    (let [line (render-type-line {:type :Record
                                  :processed 500
                                  :total 1000
                                  :pct 50.0})]
      (is (string? line))
      (is (.contains line "Record"))
      (is (.contains line "50.0%"))
      (is (.contains line "500"))
      (is (.contains line "1,000")))))

;; --- render-frame ---

(deftest render-frame-test
  (let [snap {:types [{:type :Record :processed 500 :total 1000 :pct 50.0}
                       {:type :Workout :processed 10 :total 20 :pct 50.0}]
              :overall-processed 510
              :overall-total 1020
              :overall-pct 50.0
              :rate 100
              :eta-secs 5
              :elapsed-secs 65}
        lines (render-frame snap "export.xml")]
    (testing "returns a vector of strings"
      (is (vector? lines))
      (is (every? string? lines)))
    (testing "header contains filename"
      (is (.contains (first lines) "export.xml")))
    (testing "has divider lines"
      (is (some #(.contains % "─") lines)))
    (testing "has type lines for each type"
      (is (some #(.contains % "Record") lines))
      (is (some #(.contains % "Workout") lines)))
    (testing "summary line contains overall stats"
      (let [summary (last lines)]
        (is (.contains summary "50.0%"))
        (is (.contains summary "1:05"))))))
