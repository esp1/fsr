(ns esp1.fsr.cache-performance-test
  "Performance validation tests for cache functionality"
  (:require
   [clojure.test :refer [deftest is testing]]
   [esp1.fsr.cache :as cache]))

;; T011: Performance validation test
(deftest cache-performance-test
  (testing "cache hit performance vs miss"
    (cache/clear!)
    (cache/configure! {:max-entries 1000 :enabled? true})

    ;; Populate cache with 100 URIs
    (dotimes [i 100]
      (let [uri (str "/api/resource/" i)
            root-path "/var/www/html"
            resolved-path (str "/var/www/html/api/resource/" i ".clj")
            params {"id" (str i)}]
        (cache/put! uri root-path resolved-path params)))

    ;; Measure cache hit time
    (let [test-uri "/api/resource/50"
          test-root "/var/www/html"
          iterations 1000
          start-time (System/nanoTime)]

      ;; Perform 1000 cache hits
      (dotimes [_ iterations]
        (cache/get test-uri test-root))

      (let [end-time (System/nanoTime)
            total-time-ms (/ (- end-time start-time) 1000000.0)
            avg-time-ms (/ total-time-ms iterations)]

        (is (< avg-time-ms 1.0)
            (str "Average cache hit time should be < 1ms, was " avg-time-ms "ms"))

        (println (format "Cache hit performance: %.4f ms average over %d iterations"
                         avg-time-ms iterations)))))

  (testing "cache performance with 100 unique URIs"
    (cache/clear!)

    ;; Warm up - add 100 entries
    (dotimes [i 100]
      (cache/put! (str "/page/" i) "/var/www" (str "/var/www/page/" i ".clj") {}))

    ;; Measure random access performance
    (let [iterations 1000
          start-time (System/nanoTime)]

      (dotimes [_ iterations]
        (let [random-idx (rand-int 100)]
          (cache/get (str "/page/" random-idx) "/var/www")))

      (let [end-time (System/nanoTime)
            total-time-ms (/ (- end-time start-time) 1000000.0)
            avg-time-ms (/ total-time-ms iterations)]

        (is (< avg-time-ms 1.0)
            (str "Random cache access should be < 1ms, was " avg-time-ms "ms"))

        (println (format "Random access performance: %.4f ms average over %d iterations"
                         avg-time-ms iterations)))))

  (testing "cache metrics overhead"
    (cache/clear!)

    ;; Measure time with metrics tracking
    (let [iterations 1000
          start-time (System/nanoTime)]

      (dotimes [i iterations]
        (cache/put! (str "/test/" i) "/var/www" (str "/var/www/test/" i ".clj") {})
        (cache/get (str "/test/" i) "/var/www")
        (cache/get-metrics))

      (let [end-time (System/nanoTime)
            total-time-ms (/ (- end-time start-time) 1000000.0)
            avg-time-per-op-ms (/ total-time-ms (* iterations 3))]

        (is (< avg-time-per-op-ms 1.0)
            (str "Operations with metrics should be fast, was " avg-time-per-op-ms "ms"))

        (println (format "Metrics overhead: %.4f ms average per operation over %d iterations"
                         avg-time-per-op-ms iterations)))))

  (testing "eviction performance with large cache"
    ;; Reset cache state and metrics
    (cache/clear!)
    (cache/configure! {:max-entries 1000 :eviction-policy :lru})

    ;; Reset metrics to zero
    (cache/clear!)

    ;; Fill cache to capacity
    (dotimes [i 1000]
      (cache/put! (str "/page/" i) "/var/www" (str "/var/www/page/" i ".clj") {}))

    ;; Get baseline evictions before our test
    (let [baseline-evictions (:evictions (cache/get-metrics))]

      ;; Measure eviction performance when adding beyond capacity
      (let [iterations 100
            start-time (System/nanoTime)]

        (dotimes [i iterations]
          (cache/put! (str "/page/" (+ 1000 i)) "/var/www"
                     (str "/var/www/page/" (+ 1000 i) ".clj") {}))

        (let [end-time (System/nanoTime)
              total-time-ms (/ (- end-time start-time) 1000000.0)
              avg-time-ms (/ total-time-ms iterations)]

          (is (< avg-time-ms 10.0)
              (str "Eviction should be fast, was " avg-time-ms "ms"))

          (println (format "Eviction performance: %.4f ms average over %d evictions"
                           avg-time-ms iterations))

          ;; Verify evictions occurred
          (let [metrics (cache/get-metrics)
                evictions-during-test (- (:evictions metrics) baseline-evictions)]
            (is (= iterations evictions-during-test)
                (str "Should have evicted exactly " iterations " entries, but evicted " evictions-during-test))))))))
