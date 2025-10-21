# Technical Documentation: Cache Module

**Module**: `esp1.fsr.cache`
**File**: `src/esp1/fsr/cache.clj`
**Purpose**: Performance optimization through route resolution caching

## Overview

The cache module provides an in-memory caching layer for route resolution results. It significantly improves performance by avoiding repeated filesystem scans and namespace loading for frequently accessed routes.

## Architecture

### Cache Data Structure

The cache is implemented as a Clojure atom containing a map:

```clojure
(def route-cache
  (atom {}))
```

**Cache Entry Format**:
```clojure
{"/about" {:namespace 'my-app.routes.about
           :handler-fn #function[...]
           :metadata {...}
           :path-params-pattern nil}
 "/user/123" {:namespace 'my-app.routes.user.<id>
              :handler-fn #function[...]
              :metadata {...}
              :path-params-pattern {"id" "123"}}}
```

### Key Functions

#### `clear!`
Clears all entries from the route cache.

**Signature**:
```clojure
(clear!)
→ nil
```

**Implementation**:
```clojure
(defn clear! []
  (reset! route-cache {}))
```

**Usage**:
- Called automatically in development mode before each request
- Can be called manually for cache invalidation
- Thread-safe operation via atom semantics

#### `get-cached`
Retrieves a cached route resolution result.

**Signature**:
```clojure
(get-cached uri)
→ route-result | nil
```

**Implementation**:
```clojure
(defn get-cached [uri]
  (@route-cache uri))
```

**Returns**:
- Cached route data if present
- `nil` if URI not in cache (cache miss)

#### `put-cached!`
Stores a route resolution result in the cache.

**Signature**:
```clojure
(put-cached! uri route-result)
→ route-result
```

**Implementation**:
```clojure
(defn put-cached! [uri route-result]
  (swap! route-cache assoc uri route-result)
  route-result)
```

**Behavior**:
- Returns the stored value for convenience
- Overwrites existing cache entry if present
- Thread-safe via atom swap

## Caching Strategy

### Cache Key

The cache key is the full request URI string:
- **Included**: Path portion of URI
- **Excluded**: Query parameters, fragments
- **Case**: Preserved as-is (case-sensitive)

**Examples**:
```clojure
;; These are different cache keys:
"/about"
"/About"
"/about/"

;; Query params not part of key:
"/search?q=clojure" → key is "/search"
```

### Cache Value

Cached values contain everything needed to handle a request without re-resolution:

```clojure
{:namespace    'my-app.routes.about      ; Matched namespace symbol
 :handler-fn   #function[...]             ; Resolved handler function
 :metadata     {:endpoint/http {...}}    ; Namespace metadata
 :path-params-pattern nil}               ; Parameter extraction info
```

## Development vs Production Modes

### Mode Detection

```clojure
(defn development-mode? []
  (try
    (require 'clojure.tools.namespace.repl)
    true
    (catch Exception _
      false)))
```

If `clojure.tools.namespace.repl` is available → Development mode
Otherwise → Production mode

### Development Mode Behavior

**Goal**: Enable hot-reloading of code changes

**Strategy**:
1. Clear cache before each request
2. Force full route resolution every time
3. Namespace reloading via tools.namespace
4. See code changes immediately

**Performance Trade-off**:
- Each request pays full resolution cost (~5-10ms)
- Acceptable for development with low traffic
- Enables rapid iteration without restarts

**Implementation in Ring Middleware**:
```clojure
(defn wrap-fs-router [handler root-fs-path]
  (fn [request]
    (when (development-mode?)
      (cache/clear!))
    (handle-request request root-fs-path)))
```

### Production Mode Behavior

**Goal**: Maximum performance

**Strategy**:
1. Cache persists for application lifetime
2. First request resolves and caches route
3. Subsequent requests use cached result
4. No automatic invalidation

**Performance Benefit**:
- Cache hit: < 0.1ms (simple map lookup)
- Cache miss: ~5-10ms (only on first request)
- 50-100x faster for cached routes

**Cache Lifetime**:
- Persists until application restart
- Or manual `clear!` call
- No automatic expiration

## Thread Safety

### Atom Semantics

All cache operations use Clojure atoms, providing:
- **Atomic updates**: State changes are atomic
- **No locks**: Lock-free concurrent access
- **Consistency**: Readers see consistent state

### Concurrent Access Patterns

#### Multiple Readers (Cache Hits)
```clojure
;; Thread 1
(@route-cache "/about")  ; Read

;; Thread 2
(@route-cache "/about")  ; Read

;; No contention, both reads succeed
```

#### Concurrent Cache Miss
```clojure
;; Thread 1 and Thread 2 both miss cache for same URI
;; Both perform full resolution
;; Both call put-cached!

;; Result: Last write wins (both store same data anyway)
```

#### Read During Write
```clojure
;; Thread 1: Reading
(@route-cache uri)

;; Thread 2: Writing simultaneously
(swap! route-cache assoc uri result)

;; Thread 1 sees either old or new state, both valid
```

## Performance Characteristics

### Time Complexity

| Operation | Complexity | Typical Time |
|-----------|------------|--------------|
| `get-cached` | O(1) | < 0.1ms |
| `put-cached!` | O(1) | < 0.1ms |
| `clear!` | O(1) | < 0.1ms |

### Space Complexity

Memory usage: O(n) where n = number of unique URIs

**Typical memory per cache entry**: ~1KB
- Namespace symbol: ~100 bytes
- Handler function reference: ~200 bytes
- Metadata: ~500 bytes
- Path params pattern: ~200 bytes

**Example memory usage**:
- 100 routes: ~100KB
- 1,000 routes: ~1MB
- 10,000 routes: ~10MB

For most applications, memory usage is negligible.

## Cache Warming

Currently, the cache is lazy (populated on first request). Future enhancements could include:

### Manual Warming
```clojure
(defn warm-cache! [root-fs-path]
  (doseq [uri (discover-all-uris root-fs-path)]
    (resolve-and-cache uri root-fs-path)))
```

**Benefits**:
- Predictable startup time
- First request as fast as subsequent ones
- Useful for load testing

**Trade-offs**:
- Longer application startup
- May cache routes that are never accessed

## Monitoring and Debugging

### Cache Statistics (Future Enhancement)

```clojure
(def cache-stats
  (atom {:hits 0
         :misses 0
         :evictions 0}))

(defn cache-hit-rate []
  (let [{:keys [hits misses]} @cache-stats]
    (/ hits (+ hits misses))))
```

### Cache Inspection

```clojure
;; View all cached URIs
(keys @route-cache)

;; View cache size
(count @route-cache)

;; Inspect specific entry
(@route-cache "/about")
```

## Integration with Other Modules

### With Core Module

Core module provides the resolution logic:
```clojure
(defn resolve-route [root-fs-path uri]
  (if-let [cached (cache/get-cached uri)]
    cached
    (let [result (core/uri->file root-fs-path uri)]
      (cache/put-cached! uri result)
      result)))
```

### With Ring Module

Ring middleware manages cache lifecycle:
```clojure
(defn wrap-fs-router [handler root-fs-path]
  (fn [request]
    ;; Clear cache in dev mode
    (when (development-mode?)
      (cache/clear!))

    ;; Use cache-aware resolution
    (handle-with-cache request root-fs-path)))
```

### With Static Module

Static generation may or may not use cache:
- **Option 1**: Clear cache before generation (ensure fresh scan)
- **Option 2**: Use cache for performance (if warming done first)

## Testing

### Unit Tests

```clojure
(deftest cache-operations
  (testing "put and get"
    (cache/clear!)
    (cache/put-cached! "/test" {:namespace 'test})
    (is (= {:namespace 'test} (cache/get-cached "/test"))))

  (testing "clear"
    (cache/put-cached! "/test" {:namespace 'test})
    (cache/clear!)
    (is (nil? (cache/get-cached "/test")))))
```

### Integration Tests

```clojure
(deftest cache-integration
  (testing "cache improves performance"
    (let [start (System/nanoTime)
          _ (resolve-route root "/about")  ; Cache miss
          miss-time (- (System/nanoTime) start)

          start (System/nanoTime)
          _ (resolve-route root "/about")  ; Cache hit
          hit-time (- (System/nanoTime) start)]

      (is (< hit-time (/ miss-time 10))))))  ; Cache should be 10x faster
```

## Future Enhancements

### LRU Eviction

For very large sites, implement size-limited cache with LRU eviction:

```clojure
(def max-cache-size 10000)

(defn put-cached! [uri result]
  (when (>= (count @route-cache) max-cache-size)
    (evict-lru!))
  (swap! route-cache assoc uri result))
```

### Selective Invalidation

Instead of clearing entire cache, invalidate specific routes:

```clojure
(defn invalidate! [uri-pattern]
  (swap! route-cache
         (fn [cache]
           (reduce-kv
             (fn [m k v]
               (if (re-matches uri-pattern k)
                 m
                 (assoc m k v)))
             {}
             cache))))
```

### Persistent Cache

Save cache to disk between restarts:

```clojure
(defn save-cache! [file]
  (spit file (pr-str @route-cache)))

(defn load-cache! [file]
  (reset! route-cache (read-string (slurp file))))
```

## Configuration Options (Future)

```clojure
{:cache/enabled true
 :cache/max-size 10000
 :cache/eviction-policy :lru
 :cache/persistent false
 :cache/file ".fsr-cache.edn"}
```

## Related Documentation

- [Route Caching Spec](../spec/route-caching.md) - Requirements for caching
- [Core Module](core-module.md) - What is being cached
- [Ring Integration](ring-integration.md) - When cache is used
