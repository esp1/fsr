(ns esp1.fsr.cache.contract-test
  "Contract tests for cache API validation using Malli schemas"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.test.check.generators :as gen]
   [esp1.fsr.schema :as fsr-schema]
   [malli.core :as m]
   [malli.dev :as mdev]
   [malli.generator :as mg]
   [malli.registry :as mr]))

(use-fixtures :once
  (fn [f]
    (mr/set-default-registry!
     (merge
      (m/comparator-schemas)
      (m/type-schemas)
      (m/sequence-schemas)
      (m/base-schemas)
      (fsr-schema/file-schemas)
      (fsr-schema/cache-schemas)
      (fsr-schema/route-schemas)))
    (mdev/start!)
    (f)
    (mdev/stop!)
    (mr/set-default-registry! m/default-registry)))

;; T002: Contract test for cache-key validation
(deftest cache-key-validation-test
  (testing "valid cache keys"
    (let [valid-key {:uri "/api/users/123"
                     :root-path "/var/www/html"}]
      (is (m/validate :cache-key valid-key)
          "Valid cache key should pass validation")))

  (testing "invalid cache keys"
    (is (not (m/validate :cache-key {}))
        "Empty map should fail validation")
    (is (not (m/validate :cache-key {:uri "/api/users"}))
        "Missing root-path should fail validation")
    (is (not (m/validate :cache-key {:root-path "/var/www"}))
        "Missing uri should fail validation")
    (is (not (m/validate :cache-key {:uri 123 :root-path "/var/www"}))
        "Non-string uri should fail validation"))

  (testing "property-based cache-key validation"
    (let [key-gen (mg/generator :cache-key)]
      (dotimes [_ 100]
        (let [generated-key (gen/generate key-gen)]
          (is (m/validate :cache-key generated-key)
              "Generated cache keys should always be valid"))))))

;; T003: Contract test for cache-entry validation
(deftest cache-entry-validation-test
  (testing "valid cache entries"
    (let [valid-entry {:uri "/api/users/123"
                       :root-path "/var/www/html"
                       :resolved-path "/var/www/html/api/users/<id>.clj"
                       :params {"id" "123"}
                       :timestamp 1234567890}]
      (is (m/validate :cache-entry valid-entry)
          "Valid cache entry should pass validation")))

  (testing "valid cache entries with optional metadata"
    (let [entry-with-metadata {:uri "/api/users/123"
                               :root-path "/var/www/html"
                               :resolved-path "/var/www/html/api/users/<id>.clj"
                               :params {"id" "123"}
                               :timestamp 1234567890
                               :metadata {:cached-at "2025-01-01"}}]
      (is (m/validate :cache-entry entry-with-metadata)
          "Cache entry with metadata should pass validation")))

  (testing "invalid cache entries"
    (is (not (m/validate :cache-entry {}))
        "Empty map should fail validation")
    (is (not (m/validate :cache-entry {:uri "/api/users"
                                        :root-path "/var/www"
                                        :resolved-path "/var/www/api/users.clj"
                                        :params {}
                                        :timestamp 0}))
        "Zero timestamp should fail validation (must be positive)")
    (is (not (m/validate :cache-entry {:uri "/api/users"
                                        :root-path "/var/www"
                                        :resolved-path "/var/www/api/users.clj"
                                        :params {}
                                        :timestamp -123}))
        "Negative timestamp should fail validation"))

  (testing "params field validation"
    (is (m/validate :cache-entry {:uri "/api/users"
                                   :root-path "/var/www"
                                   :resolved-path "/var/www/api/users.clj"
                                   :params {}
                                   :timestamp 123})
        "Empty params map should be valid")
    (is (not (m/validate :cache-entry {:uri "/api/users"
                                        :root-path "/var/www"
                                        :resolved-path "/var/www/api/users.clj"
                                        :params nil
                                        :timestamp 123}))
        "Nil params should fail validation")))

;; T004: Contract test for cache-config validation
(deftest cache-config-validation-test
  (testing "valid cache configurations"
    (is (m/validate :cache-config {:max-entries 1000
                                    :enabled? true
                                    :eviction-policy :lru})
        "Full config with all fields should pass validation")
    (is (m/validate :cache-config {})
        "Empty config should pass validation (uses defaults)"))

  (testing "default values"
    (let [empty-config {}]
      (is (m/validate :cache-config empty-config)
          "Empty config should be valid with defaults")))

  (testing "eviction policy validation"
    (is (m/validate :cache-config {:eviction-policy :lru})
        "LRU eviction policy should be valid")
    (is (m/validate :cache-config {:eviction-policy :fifo})
        "FIFO eviction policy should be valid")
    (is (m/validate :cache-config {:eviction-policy :none})
        "None eviction policy should be valid")
    (is (not (m/validate :cache-config {:eviction-policy :invalid}))
        "Invalid eviction policy should fail validation"))

  (testing "max-entries validation"
    (is (m/validate :cache-config {:max-entries 1})
        "Minimum max-entries should be valid")
    (is (m/validate :cache-config {:max-entries 10000})
        "Large max-entries should be valid")
    (is (not (m/validate :cache-config {:max-entries 0}))
        "Zero max-entries should fail validation")
    (is (not (m/validate :cache-config {:max-entries -1}))
        "Negative max-entries should fail validation"))

  (testing "enabled? validation"
    (is (m/validate :cache-config {:enabled? true})
        "Enabled true should be valid")
    (is (m/validate :cache-config {:enabled? false})
        "Enabled false should be valid")
    (is (not (m/validate :cache-config {:enabled? "true"}))
        "String enabled? should fail validation")))

;; T005: Contract test for cache-metrics validation
(deftest cache-metrics-validation-test
  (testing "valid cache metrics"
    (let [valid-metrics {:hits 100
                         :misses 50
                         :evictions 10
                         :current-size 500}]
      (is (m/validate :cache-metrics valid-metrics)
          "Valid metrics should pass validation")))

  (testing "zero counters are valid"
    (let [zero-metrics {:hits 0
                        :misses 0
                        :evictions 0
                        :current-size 0}]
      (is (m/validate :cache-metrics zero-metrics)
          "Zero counters should be valid")))

  (testing "invalid metrics"
    (is (not (m/validate :cache-metrics {:hits -1
                                          :misses 0
                                          :evictions 0
                                          :current-size 0}))
        "Negative hits should fail validation")
    (is (not (m/validate :cache-metrics {:hits 0
                                          :misses -1
                                          :evictions 0
                                          :current-size 0}))
        "Negative misses should fail validation")
    (is (not (m/validate :cache-metrics {:hits 0
                                          :misses 0
                                          :evictions -1
                                          :current-size 0}))
        "Negative evictions should fail validation")
    (is (not (m/validate :cache-metrics {:hits 0
                                          :misses 0
                                          :evictions 0
                                          :current-size -1}))
        "Negative current-size should fail validation"))

  (testing "missing fields"
    (is (not (m/validate :cache-metrics {}))
        "Empty metrics should fail validation")
    (is (not (m/validate :cache-metrics {:hits 0 :misses 0}))
        "Missing evictions and current-size should fail validation"))

  (testing "computed hit-rate logic"
    ;; Note: hit-rate is computed, not validated by schema
    ;; This test ensures the schema doesn't require it
    (let [metrics {:hits 100
                   :misses 50
                   :evictions 10
                   :current-size 500}]
      (is (m/validate :cache-metrics metrics)
          "Metrics without hit-rate field should be valid"))))
