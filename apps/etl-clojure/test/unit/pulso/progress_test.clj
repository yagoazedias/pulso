(ns pulso.progress-test
  (:require [clojure.test :refer [deftest is testing]]
            [pulso.progress :as progress]))

(deftest make-state-initializes-correctly
  (let [totals {:Record 1000 :Workout 50 :Correlation 20 :ActivitySummary 365 :Me 1}
        state  @(progress/make-state totals)]
    (testing "only tracks known types"
      (is (= #{:Record :Workout :Correlation :ActivitySummary}
             (set (keys (:totals state)))))
      (is (nil? (get-in state [:totals :Me]))))
    (testing "all processed counts start at zero"
      (is (every? zero? (vals (:processed state)))))))

(deftest record-progress-increments
  (let [state-atom (progress/make-state {:Record 100 :Workout 10
                                          :Correlation 5 :ActivitySummary 30})]
    (progress/record-progress! state-atom :Record)
    (progress/record-progress! state-atom :Record)
    (progress/record-progress! state-atom :Workout)
    (is (= 2 (get-in @state-atom [:processed :Record])))
    (is (= 1 (get-in @state-atom [:processed :Workout])))
    (is (= 0 (get-in @state-atom [:processed :Correlation])))))

(deftest record-progress-ignores-untracked
  (let [state-atom (progress/make-state {:Record 100})]
    (progress/record-progress! state-atom :ExportDate)
    (progress/record-progress! state-atom :Me)
    (is (= 0 (get-in @state-atom [:processed :Record])))))

(deftest snapshot-calculates-percentages
  (let [state {:totals    {:Record 1000 :Workout 50 :Correlation 20 :ActivitySummary 365}
               :processed {:Record 500 :Workout 50 :Correlation 10 :ActivitySummary 0}
               :start-ms  (- (System/currentTimeMillis) 10000)
               :phase     :processing}
        snap  (progress/snapshot state)]
    (testing "per-type percentages"
      (let [record-info (first (filter #(= :Record (:type %)) (:types snap)))]
        (is (= 500 (:processed record-info)))
        (is (= 1000 (:total record-info)))
        (is (< 49.9 (:pct record-info) 50.1))))
    (testing "workout is 100%"
      (let [workout-info (first (filter #(= :Workout (:type %)) (:types snap)))]
        (is (= 100.0 (:pct workout-info)))))
    (testing "overall stats"
      (is (= 560 (:overall-processed snap)))
      (is (= 1435 (:overall-total snap)))
      (is (< 38.0 (:overall-pct snap) 40.0)))
    (testing "rate and ETA are positive"
      (is (pos? (:rate snap)))
      (is (pos? (:eta-secs snap))))))

(deftest snapshot-handles-zero-totals
  (let [state {:totals    {:Record 0 :Workout 0 :Correlation 0 :ActivitySummary 0}
               :processed {:Record 0 :Workout 0 :Correlation 0 :ActivitySummary 0}
               :start-ms  (System/currentTimeMillis)
               :phase     :processing}
        snap  (progress/snapshot state)]
    (is (= 0.0 (:overall-pct snap)))
    (is (= 0 (:eta-secs snap)))))
