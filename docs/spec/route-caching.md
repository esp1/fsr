# Feature Specification: Route Caching System

## Overview

The Route Caching System optimizes performance by caching the results of route resolution, reducing repeated filesystem scans and namespace loading operations for frequently accessed routes.

## User Value

Applications experience faster response times because route resolution happens only once per unique URI, with subsequent requests served from an in-memory cache.

## User Stories

### Primary Story
**As a** web application operator
**I want** route resolution to be cached after the first lookup
**So that** my application responds faster to repeated requests

### Supporting Stories

1. **Automatic Caching**
   - **As a** developer
   - **I want** caching to happen automatically without configuration
   - **So that** I get performance benefits without extra work

2. **Development Mode Invalidation**
   - **As a** developer
   - **I want** the cache to clear on each request during development
   - **So that** I see my code changes immediately without manual cache clearing

3. **Production Mode Persistence**
   - **As a** developer
   - **I want** the cache to persist across requests in production
   - **So that** my application gets maximum performance benefit

## Functional Requirements

### FR-001: Route Resolution Caching
The system MUST cache the results of route resolution including matched namespace, handler function, and path parameter patterns.

**Acceptance Criteria**:
- First request to a URI performs full route resolution
- Result is stored in cache with URI as key
- Subsequent requests use cached result
- Cache stores namespace symbol, handler function, and parameter extraction pattern

### FR-002: Cache Key Strategy
The system MUST use the request URI as the cache key for route lookups.

**Acceptance Criteria**:
- Cache key is the full URI string
- Query parameters are excluded from the key (only path matters)
- URIs are normalized before caching
- Case-sensitive matching is used

### FR-003: Automatic Cache Invalidation (Development)
The system MUST automatically clear the route cache on each request when running in development mode.

**Acceptance Criteria**:
- Development mode is detected by presence of tools.namespace on classpath
- Cache is completely cleared before each request
- No manual intervention required
- Allows code changes to be seen immediately

### FR-004: Cache Persistence (Production)
The system MUST persist the route cache across requests when running in production mode.

**Acceptance Criteria**:
- Production mode is detected by absence of tools.namespace on classpath
- Cache persists for the lifetime of the application
- Only cleared on application restart
- Maximum performance benefit

### FR-005: Thread-Safe Cache Operations
The system MUST ensure cache operations are thread-safe for concurrent request handling.

**Acceptance Criteria**:
- Cache is implemented using Clojure atoms or similar thread-safe mechanism
- Concurrent reads do not block each other
- Cache updates are atomic
- No race conditions in cache access

### FR-006: Manual Cache Clearing
The system MUST provide a function to manually clear the route cache.

**Acceptance Criteria**:
- Function `clear-route-cache!` is available
- Clears all cached entries
- Useful for programmatic cache management
- Thread-safe operation

Example:
```clojure
(require '[esp1.fsr.core :refer [clear-route-cache!]])
(clear-route-cache!)
```

### FR-007: Cache Miss Handling
The system MUST gracefully handle cache misses by performing full route resolution.

**Acceptance Criteria**:
- Cache miss triggers normal route resolution
- Result is stored in cache after resolution
- No errors or warnings for cache misses
- Transparent to the application

### FR-008: Cache Hit Performance
The system MUST provide fast cache lookups that avoid filesystem access and namespace loading.

**Acceptance Criteria**:
- Cache hits do not access the filesystem
- Cache hits do not reload namespaces
- Lookup time is O(1) or near constant time
- Significantly faster than full resolution

## Non-Functional Requirements

### Performance
- Cache lookup should be < 0.1ms
- Full route resolution (cache miss) should be < 10ms
- Cache should handle thousands of unique routes efficiently
- Memory usage should be reasonable for typical applications

### Reliability
- Cache should never corrupt application state
- Cache errors should not prevent route handling
- Cache clearing should be atomic

### Developer Experience
- Automatic mode detection (dev vs production)
- No configuration required for typical usage
- Clear function for manual control

## Edge Cases

1. **First Request**: Cache miss, full resolution, store result
2. **Memory Constraints**: Very large sites may need cache size limits (future enhancement)
3. **Cache Corruption**: If cache contains invalid data, fallback to full resolution
4. **Concurrent First Requests**: Multiple requests to same URI may resolve concurrently, last write wins
5. **Development Mode Detection**: If tools.namespace is on classpath but production mode desired, manual clearing needed

## Cache Data Structure

The cache stores:
```clojure
{"/about" {:namespace 'my-app.routes.about
           :handler-fn #function[my-app.routes.about/GET-about]
           :path-params-pattern nil}
 "/user/123" {:namespace 'my-app.routes.user.<id>
              :handler-fn #function[my-app.routes.user.<id>/GET-user]
              :path-params-pattern {"id" "123"}}}
```

## Cache Lifecycle

### Development Mode
```
Request → Clear Cache → Resolve Route → Store in Cache → Handle Request
                    ↑                                  ↓
                    └──────────── Next Request ────────┘
```

### Production Mode
```
Request → Check Cache → Hit? → Use Cached Result → Handle Request
               ↓
             Miss?
               ↓
          Resolve Route → Store in Cache → Handle Request
                              ↓
                      (Persists for future requests)
```

## Mode Detection Logic

```clojure
(defn development-mode? []
  (try
    (require 'clojure.tools.namespace.repl)
    true
    (catch Exception _
      false)))
```

If `clojure.tools.namespace.repl` can be required → development mode
Otherwise → production mode

## Performance Impact

### Without Caching
- Every request: Filesystem scan + namespace load + metadata resolution
- Typical time: 5-10ms per request
- 100 req/sec: 500-1000ms total overhead

### With Caching (Production)
- First request: 5-10ms (cache miss)
- Subsequent requests: < 0.1ms (cache hit)
- 100 req/sec: ~5-10ms total overhead (assuming cache warmed up)

## Integration Example

```clojure
;; In core.clj
(def route-cache (atom {}))

(defn clear-route-cache! []
  (reset! route-cache {}))

(defn resolve-route [uri]
  (if-let [cached (@route-cache uri)]
    cached
    (let [result (perform-full-resolution uri)]
      (swap! route-cache assoc uri result)
      result)))

;; In ring.clj
(defn wrap-fs-router [handler root-fs-path]
  (fn [request]
    (when (development-mode?)
      (clear-route-cache!))
    (if-let [route (resolve-route (:uri request))]
      (handle-route route request)
      (handler request))))
```

## Future Enhancements

- **Cache Size Limits**: LRU eviction for very large sites
- **Selective Invalidation**: Clear only specific routes instead of entire cache
- **Cache Warming**: Pre-populate cache on startup
- **Cache Statistics**: Track hit/miss rates for monitoring
- **Persistent Cache**: Save cache to disk between restarts

## Dependencies

- Requires URI to File Routing for route resolution
- Integrates with Ring Middleware for cache management
- Optional dependency on tools.namespace for mode detection

## Related Requirements

- [URI to File Routing](uri-to-file-routing.md) - Describes what is cached
- [Ring Middleware Integration](ring-middleware.md) - Describes when cache is used
