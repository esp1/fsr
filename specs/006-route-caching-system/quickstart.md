# Quickstart: Route Caching System

## Overview
This guide demonstrates the route caching functionality added to the FSR library. The cache improves performance by storing resolved route mappings in memory to avoid repeated filesystem operations.

## Basic Usage

### 1. Enable Caching (Default)
```clojure
(require '[esp1.fsr.cache :as cache])

;; Caching is enabled by default with 1000 entry limit
;; No additional setup required for basic usage
```

### 2. Route Resolution with Caching
```clojure
(require '[esp1.fsr.core :as fsr])

;; First call - resolves from filesystem and caches result
(def result1 (fsr/resolve-route "/api/users/123" "/var/www/html"))
;; => {:uri "/api/users/123", :resolved-path "/var/www/html/api/users/<id>.clj",
;;     :params {:id "123"}, :cached? false}

;; Second call - returns cached result (faster)
(def result2 (fsr/resolve-route "/api/users/123" "/var/www/html"))
;; => {:uri "/api/users/123", :resolved-path "/var/www/html/api/users/<id>.clj",
;;     :params {:id "123"}, :cached? true}
```

### 3. Cache Metrics
```clojure
(cache/get-metrics)
;; => {:hits 1, :misses 1, :evictions 0, :current-size 1, :hit-rate 0.5}
```

## Configuration

### Custom Cache Limits
```clojure
;; Configure cache for high-traffic application
(cache/configure! {:max-entries 5000
                   :enabled? true
                   :eviction-policy :lru})
```

### Disable Caching
```clojure
;; Disable caching for development
(cache/configure! {:enabled? false})
```

## Cache Management

### Clear Entire Cache
```clojure
;; Clear all cached entries
(cache/clear!)
;; => 42  ; Returns number of entries cleared
```

### Pattern-Based Invalidation
```clojure
;; Invalidate all API routes
(cache/invalidate! #"/api/.*")
;; => 15  ; Returns number of entries invalidated

;; Invalidate specific user routes
(cache/invalidate! #"/api/users/\d+")
;; => 8   ; Returns number of entries invalidated
```

## Development Workflow

### Hot Reloading Support
```clojure
;; After modifying route files, invalidate affected routes
(cache/invalidate! #"/api/.*")

;; Or clear entire cache for safety
(cache/clear!)
```

### Testing with Clean Cache
```clojure
(deftest my-routing-test
  (cache/clear!)  ; Start with empty cache
  (testing "route resolution"
    ;; Your test code here
    ))
```

## Performance Validation

### Measure Cache Performance
```clojure
(require '[criterium.core :as crit])

;; Warm up cache
(fsr/resolve-route "/api/users/123" "/var/www/html")

;; Benchmark cached vs uncached
(cache/clear!)
(crit/quick-bench (fsr/resolve-route "/api/users/123" "/var/www/html"))
;; First call: ~5ms (filesystem access)

(crit/quick-bench (fsr/resolve-route "/api/users/123" "/var/www/html"))
;; Subsequent calls: ~0.1ms (cache hit)
```

### Monitor Cache Health
```clojure
;; Check cache effectiveness
(let [metrics (cache/get-metrics)]
  (when (< (:hit-rate metrics) 0.7)
    (println "Warning: Low cache hit rate" (:hit-rate metrics))))
```

## Integration Examples

### Ring Middleware
```clojure
(defn with-route-caching [handler]
  (fn [request]
    ;; Cache is automatically used by FSR resolution
    (handler request)))
```

### Web Application
```clojure
(defn -main []
  ;; Configure cache for production
  (cache/configure! {:max-entries 10000})

  ;; Start web server
  (start-server))
```

## Troubleshooting

### Common Issues

1. **Low Hit Rate**: Increase cache size or check for dynamic route patterns
2. **Memory Usage**: Monitor with `(cache/get-metrics)` and adjust `max-entries`
3. **Stale Routes**: Use pattern invalidation after file changes

### Debug Information
```clojure
;; Get detailed cache state (development only)
(cache/debug-info)
;; => {:config {...}, :metrics {...}, :sample-keys [...]}
```

This quickstart covers the essential cache operations needed for most applications. The cache automatically integrates with existing FSR routing without requiring code changes.