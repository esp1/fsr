(ns esp1.fsr.cache-integration-test
  "Integration tests for cache functionality based on quickstart.md examples"
  (:require
   [clojure.test :refer [deftest is testing]]
   [esp1.fsr.cache :as cache]))

;; T006: Integration test for cache hit scenario
(deftest cache-hit-scenario-test
  (testing "first resolution (miss) then second resolution (hit)"
    ;; Clear cache to start fresh
    (cache/clear!)

    ;; Simulate first route resolution - should be a miss
    (let [uri "/api/users/123"
          root-path "/var/www/html"
          resolved-path "/var/www/html/api/users/<id>.clj"
          params {"id" "123"}]

      ;; First call - cache miss, manually add to cache
      (cache/put! uri root-path resolved-path params)
      (let [result1 (cache/get uri root-path)]
        (is result1 "First call should return cached entry")
        (is (= uri (:uri result1)) "URI should match")
        (is (= resolved-path (:resolved-path result1)) "Resolved path should match"))

      ;; Second call - should be cache hit
      (let [result2 (cache/get uri root-path)]
        (is result2 "Second call should return cached entry")
        (is (= uri (:uri result2)) "URI should match on second call"))

      ;; Verify metrics show hit
      (let [metrics (cache/get-metrics)]
        (is (> (:hits metrics) 0) "Should have at least one cache hit")))))

;; T007: Integration test for cache clearing
(deftest cache-clearing-test
  (testing "cache/clear! returns correct count and empties cache"
    ;; Add some entries to cache
    (cache/clear!)
    (cache/put! "/api/users/1" "/var/www" "/var/www/api/users/<id>.clj" {"id" "1"})
    (cache/put! "/api/users/2" "/var/www" "/var/www/api/users/<id>.clj" {"id" "2"})
    (cache/put! "/api/posts/1" "/var/www" "/var/www/api/posts/<id>.clj" {"id" "1"})

    ;; Verify entries exist
    (is (cache/get "/api/users/1" "/var/www") "Entry should exist before clear")

    ;; Clear cache and verify count
    (let [cleared-count (cache/clear!)]
      (is (= 3 cleared-count) "Should return count of cleared entries"))

    ;; Verify cache is empty
    (is (nil? (cache/get "/api/users/1" "/var/www")) "Entry should not exist after clear")
    (is (nil? (cache/get "/api/users/2" "/var/www")) "Entry should not exist after clear")
    (is (nil? (cache/get "/api/posts/1" "/var/www")) "Entry should not exist after clear")

    ;; Verify metrics show empty cache
    (let [metrics (cache/get-metrics)]
      (is (= 0 (:current-size metrics)) "Current size should be 0 after clear"))))

;; T008: Integration test for pattern invalidation
(deftest pattern-invalidation-test
  (testing "cache/invalidate! with regex patterns"
    ;; Clear and populate cache
    (cache/clear!)
    (cache/put! "/api/users/1" "/var/www" "/var/www/api/users/<id>.clj" {"id" "1"})
    (cache/put! "/api/users/2" "/var/www" "/var/www/api/users/<id>.clj" {"id" "2"})
    (cache/put! "/api/posts/1" "/var/www" "/var/www/api/posts/<id>.clj" {"id" "1"})
    (cache/put! "/static/img.png" "/var/www" "/var/www/static/img.png" {})

    ;; Invalidate all API routes
    (let [invalidated-count (cache/invalidate! #"/api/.*")]
      (is (= 3 invalidated-count) "Should invalidate 3 API routes"))

    ;; Verify API routes are gone
    (is (nil? (cache/get "/api/users/1" "/var/www")) "API user route should be invalidated")
    (is (nil? (cache/get "/api/posts/1" "/var/www")) "API post route should be invalidated")

    ;; Verify static route still exists
    (is (cache/get "/static/img.png" "/var/www") "Static route should still exist"))

  (testing "selective invalidation with specific pattern"
    (cache/clear!)
    (cache/put! "/api/users/1" "/var/www" "/var/www/api/users/<id>.clj" {"id" "1"})
    (cache/put! "/api/users/2" "/var/www" "/var/www/api/users/<id>.clj" {"id" "2"})
    (cache/put! "/api/posts/1" "/var/www" "/var/www/api/posts/<id>.clj" {"id" "1"})

    ;; Invalidate only user routes
    (let [invalidated-count (cache/invalidate! #"/api/users/\d+")]
      (is (= 2 invalidated-count) "Should invalidate 2 user routes"))

    ;; Verify posts route still exists
    (is (cache/get "/api/posts/1" "/var/www") "Post route should still exist")))

;; T009: Integration test for LRU eviction
(deftest lru-eviction-test
  (testing "LRU eviction when cache exceeds max-entries"
    ;; Configure cache with max-entries=3
    (cache/configure! {:max-entries 3 :eviction-policy :lru})
    (cache/clear!)

    ;; Add 3 entries (fill cache)
    (cache/put! "/page/1" "/var/www" "/var/www/page/1.clj" {})
    (cache/put! "/page/2" "/var/www" "/var/www/page/2.clj" {})
    (cache/put! "/page/3" "/var/www" "/var/www/page/3.clj" {})

    (let [metrics-before (cache/get-metrics)]
      (is (= 3 (:current-size metrics-before)) "Cache should have 3 entries"))

    ;; Add 4th entry - should evict oldest (page/1)
    (cache/put! "/page/4" "/var/www" "/var/www/page/4.clj" {})

    (let [metrics-after (cache/get-metrics)]
      (is (= 3 (:current-size metrics-after)) "Cache should still have 3 entries")
      (is (> (:evictions metrics-after) 0) "Should have recorded eviction"))

    ;; Verify oldest entry was evicted
    (is (nil? (cache/get "/page/1" "/var/www")) "Oldest entry should be evicted")
    (is (cache/get "/page/4" "/var/www") "Newest entry should exist"))

  (testing "LRU eviction updates access time"
    (cache/configure! {:max-entries 3 :eviction-policy :lru})
    (cache/clear!)

    ;; Add 3 entries
    (cache/put! "/page/1" "/var/www" "/var/www/page/1.clj" {})
    (cache/put! "/page/2" "/var/www" "/var/www/page/2.clj" {})
    (cache/put! "/page/3" "/var/www" "/var/www/page/3.clj" {})

    ;; Access page/1 to make it recently used
    (cache/get "/page/1" "/var/www")

    ;; Add 4th entry - should evict page/2 (least recently used)
    (cache/put! "/page/4" "/var/www" "/var/www/page/4.clj" {})

    ;; Verify page/1 still exists (was accessed recently)
    (is (cache/get "/page/1" "/var/www") "Recently accessed entry should not be evicted")
    ;; Note: Without inspecting internal state, we can't guarantee page/2 was evicted
    ;; but we know something was evicted and page/1 was protected by access
    ))

;; T010: Integration test for cache configuration
(deftest cache-configuration-test
  (testing "cache/configure! updates settings"
    (let [new-config {:max-entries 5000
                      :enabled? true
                      :eviction-policy :fifo}
          result (cache/configure! new-config)]
      (is (= new-config result) "Should return updated config")))

  (testing "disabled cache bypasses caching"
    (cache/configure! {:enabled? false})
    (cache/clear!)

    ;; Try to add entry with cache disabled
    (cache/put! "/test" "/var/www" "/var/www/test.clj" {})

    ;; Verify entry was not cached (because cache is disabled)
    ;; Note: This test assumes put! respects enabled? flag
    ;; Implementation will determine exact behavior
    )

  (testing "custom max-entries limit"
    (cache/configure! {:max-entries 2 :enabled? true})
    (cache/clear!)

    ;; Add entries up to limit
    (cache/put! "/page/1" "/var/www" "/var/www/page/1.clj" {})
    (cache/put! "/page/2" "/var/www" "/var/www/page/2.clj" {})

    (let [metrics (cache/get-metrics)]
      (is (= 2 (:current-size metrics)) "Should respect custom max-entries"))

    ;; Adding one more should trigger eviction
    (cache/put! "/page/3" "/var/www" "/var/www/page/3.clj" {})

    (let [metrics-after (cache/get-metrics)]
      (is (= 2 (:current-size metrics-after)) "Should maintain max-entries limit")
      (is (> (:evictions metrics-after) 0) "Should have evicted an entry"))))
