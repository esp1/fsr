# Cache Module

**Module**: `esp1.fsr.cache` (`src/esp1/fsr/cache.clj`)

Implements route caching for performance. See [requirements](../functional/route-caching.md).

## Data Structure

```clojure
(def route-cache (atom {}))

;; Entry format:
{"/about" {:namespace 'my-app.routes.about
           :handler-fn #function[...]
           :metadata {...}
           :path-params nil}
 "/user/123" {:namespace 'my-app.routes.user.<id>
              :handler-fn #function[...]
              :path-params {"id" "123"}}}
```

## Key Functions

**`clear!`** - Reset cache to empty map. Thread-safe via atom.

**`get-cached`** - Return cached result for URI, or `nil` on miss.

**`put-cached!`** - Store result, return it. Overwrites existing entries.

## Mode Detection

```clojure
(defn development-mode? []
  (try
    (require 'clojure.tools.namespace.repl)
    true
    (catch Exception _ false)))
```

## Dev vs Production Behavior

**Development** (tools.namespace present):
- Cache cleared before each request
- Full resolution every time
- ~5-10ms per request
- Enables hot-reload

**Production** (tools.namespace absent):
- Cache persists for app lifetime
- First request: ~5-10ms (miss)
- Subsequent: < 0.1ms (hit)
- 50-100x faster for cached routes

## Thread Safety

Clojure atoms provide:
- Atomic updates
- Lock-free concurrent reads
- Consistency (readers see valid state)

Concurrent cache misses for same URI: both resolve, last write wins (both store same data—harmless).

## Performance

| Operation | Time |
|-----------|------|
| `get-cached` | < 0.1ms |
| `put-cached!` | < 0.1ms |
| `clear!` | < 0.1ms |

Memory: ~1KB per cached route. 1000 routes ≈ 1MB.

## Integration

**With Ring middleware**:
```clojure
(defn wrap-fs-router [handler root-fs-path]
  (fn [request]
    (when (development-mode?)
      (cache/clear!))
    (handle-request request root-fs-path)))
```

**With core module**:
```clojure
(defn resolve-route [root-fs-path uri]
  (if-let [cached (cache/get-cached uri)]
    cached
    (let [result (core/uri->file root-fs-path uri)]
      (cache/put-cached! uri result)
      result)))
```

## Related

- [Route Caching Spec](../functional/route-caching.md) - Requirements
- [Core Module](core-module.md) - What is being cached
- [Ring Middleware](../functional/ring-middleware.md) - When cache is used
