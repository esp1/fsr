;; Cache API Contracts
;; Malli schemas for route caching functions

(ns esp1.fsr.contracts.cache
  "API contracts for route caching functionality"
  (:require [malli.core :as m]))

;; Core data schemas
(def cache-key-schema
  "Schema for cache key composite identifier"
  [:map
   [:uri :string]
   [:root-path :string]])

(def cache-entry-schema
  "Schema for cached route resolution result"
  [:map
   [:uri :string]
   [:root-path :string]
   [:resolved-path :string]
   [:params :map]
   [:timestamp pos-int?]
   [:metadata {:optional true} :map]])

(def cache-config-schema
  "Schema for cache configuration"
  [:map
   [:max-entries {:default 1000} pos-int?]
   [:enabled? {:default true} :boolean]
   [:eviction-policy {:default :lru} [:enum :lru :fifo :none]]])

(def cache-metrics-schema
  "Schema for cache performance metrics"
  [:map
   [:hits :int]
   [:misses :int]
   [:evictions :int]
   [:current-size :int]])

;; Function contracts

(def resolve-with-cache-contract
  "Contract for main cached route resolution function"
  [:=>
   [:cat :string :string]  ; [uri root-path]
   [:or cache-entry-schema :nil]])  ; Returns cache entry or nil

(def cache-clear!-contract
  "Contract for cache clearing function"
  [:=> [:cat] :int])  ; Returns number of entries cleared

(def cache-invalidate!-contract
  "Contract for pattern-based cache invalidation"
  [:=>
   [:cat :string]  ; [pattern]
   :int])  ; Returns number of entries invalidated

(def cache-metrics-contract
  "Contract for cache metrics retrieval"
  [:=> [:cat] cache-metrics-schema])

(def cache-configure!-contract
  "Contract for cache configuration updates"
  [:=>
   [:cat cache-config-schema]
   cache-config-schema])  ; Returns updated config

;; Test data generators for property-based testing
(def cache-key-gen
  [:map
   [:uri [:string {:min 1 :max 50}]]
   [:root-path [:string {:min 1 :max 100}]]])

(def sample-uris
  ["/api/users/123"
   "/static/images/logo.png"
   "/docs/getting-started"
   "/admin/dashboard"
   "/api/orders/search?status=pending"])

(def sample-root-paths
  ["/var/www/html"
   "/usr/share/nginx/html"
   "/home/user/myapp/public"
   "/app/resources/static"])

;; Contract validation helpers
(defn valid-cache-key? [data]
  (m/validate cache-key-schema data))

(defn valid-cache-entry? [data]
  (m/validate cache-entry-schema data))

(defn valid-cache-config? [data]
  (m/validate cache-config-schema data))

(defn valid-cache-metrics? [data]
  (m/validate cache-metrics-schema data))