(ns example.core-test
  (:require [clojure.test :refer :all]
            [example.core :refer :all]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))

(deftest b-test
  (is (= :a/b (keyword "a" "b"))))
