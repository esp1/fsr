(ns esp1.fsr.compile-test
  (:require [clojure.test :refer [deftest is testing]]
            [esp1.fsr.compile :refer [classify-route]]))

;; Most functionality is tested through integration tests.
;; These are basic unit tests for the public API.

(deftest test-classify-route
  (testing "Static route classification"
    (let [route {:uri "/api/users"}
          result (classify-route route)]
      (is (= :static (:route-type result)))))

  (testing "Pattern route classification with single angle brackets"
    (let [route {:uri "/thing/<id>"}
          result (classify-route route)]
      (is (= :pattern (:route-type result)))))

  (testing "Pattern route classification with double angle brackets"
    (let [route {:uri "/docs/<<path>>"}
          result (classify-route route)]
      (is (= :pattern (:route-type result))))))
