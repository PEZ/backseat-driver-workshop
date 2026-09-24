(ns try-clojure.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [try-clojure.core :as sut]))

(deftest test-mean-returns-exptect-average-value-for-a-collection
  (testing "mean of vector values"
    (let [expected 4
          actual (sut/mean [7 3 2])]
      (is (= expected actual))))
  (testing "mean of set values"
    (let [expected 4
          actual (sut/mean #{7 3 2})]
      (is (= expected actual)))))
