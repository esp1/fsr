# Route Compilation Technical Design

## Overview

This document describes the technical implementation of production route compilation, which compiles non-GET filesystem routes into efficient runtime data structures.

**Implements:** [Production Route Compilation](../spec/compiled-route-production.md)

## Architecture

### Compilation Phase (Build Time)

```
Filesystem Routes
      ↓
Route Discovery (scan .clj files)
      ↓
Metadata Extraction (load ns, read :endpoint/http)
      ↓
Route Classification (static vs parameterized)
      ↓
Compilation (generate lookup structures)
      ↓
Serialization (write compiled-routes.edn)
```

### Runtime Phase (Production)

```
Incoming Request
      ↓
Load compiled-routes.edn (at startup)
      ↓
Route Matching (static map OR pattern match)
      ↓
Handler Resolution (require ns, resolve var)
      ↓
Handler Execution (invoke with request)
```

## Data Structures

### Compiled Route Format

```clojure
{:static-routes
 {"/api/users" {:methods {:post {:ns 'my-app.routes.api.users
                                 :handler 'create-user
                                 :metadata {...}}
                          :delete {:ns 'my-app.routes.api.users
                                   :handler 'delete-all-users}}}
  "/api/posts" {:methods {:post {:ns 'my-app.routes.api.posts
                                 :handler 'create-post}}}}

 :pattern-routes
 [{:pattern #"/thing/([^/]+)"
   :uri-template "/thing/<id>"
   :param-names ["id"]
   :methods {:get {:ns 'my-app.routes.thing.<id>
                   :handler 'get-thing}
             :put {:ns 'my-app.routes.thing.<id>
                   :handler 'update-thing}}}

  {:pattern #"/docs/(.*)"
   :uri-template "/docs/<<path>>"
   :param-names ["path"]
   :methods {:get {:ns 'my-app.routes.docs.<<path>>
                   :handler 'get-doc}}}]}
```

### Design Rationale

1. **Static Routes Map**: O(1) hash lookup for non-parameterized routes
2. **Pattern Routes Vector**: Sequential matching for parameterized routes (preserves priority)
3. **Separate Methods**: Each HTTP method can have different handler/metadata
4. **Metadata Included**: All namespace metadata preserved for handler context
5. **Uri Template**: Human-readable source pattern for debugging

## Implementation Modules

### Module 1: Route Discovery & Classification

**File:** `src/esp1/fsr/compile.clj`

```clojure
(defn discover-routes
  "Scans root-fs-path and returns all route metadata.
   Returns: [{:uri \"/foo\" :file <File> :ns-sym 'app.routes.foo ...}]"
  [root-fs-path]
  ...)

(defn classify-route
  "Classifies route as :static or :pattern based on URI.
   Returns: {:type :static/:pattern :uri-template \"/foo\" ...}"
  [route-meta]
  ...)
```

**Logic:**
- Reuse `endpoint-ns-syms` from [core-module.md](core-module.md)
- Reuse `ns-sym->uri` to generate URI templates
- Classify by checking for `<param>` or `<<param>>` in URI
- Extract all HTTP methods from `:endpoint/http` metadata

### Module 2: Compilation

**File:** `src/esp1/fsr/compile.clj`

```clojure
(defn compile-static-route
  "Compiles a static route into map entry.
   Returns: [\"/api/users\" {:methods {...}}]"
  [route-meta]
  ...)

(defn compile-pattern-route
  "Compiles a parameterized route into pattern entry.
   Returns: {:pattern #\"...\" :param-names [...] :methods {...}}"
  [route-meta]
  ...)

(defn compile-routes
  "Main compilation function.
   Returns: {:static-routes {...} :pattern-routes [...]}"
  [root-fs-path]
  ...)
```

**Logic:**
- Use `filename-match-info` from [core.clj](../../src/esp1/fsr/core.clj:55-81) to generate patterns
- For each HTTP method in `:endpoint/http`, create handler entry
- Resolve `:endpoint/type` delegation chains to find concrete handlers
- Store full namespace metadata for handler context

### Module 3: Runtime Matching

**File:** `src/esp1/fsr/runtime.clj` (new)

```clojure
(defn load-compiled-routes
  "Loads compiled routes from EDN file.
   Returns: Compiled routes data structure."
  [compiled-routes-path]
  ...)

(defn match-static-route
  "Attempts static route match.
   Returns: {:handler-var <var> :metadata {...}} or nil"
  [uri method static-routes]
  ...)

(defn match-pattern-route
  "Attempts pattern route match.
   Returns: {:handler-var <var> :path-params {...} :metadata {...}} or nil"
  [uri method pattern-routes]
  ...)

(defn match-route
  "Top-level route matching.
   Returns: Match result or nil."
  [uri method compiled-routes]
  ...)
```

**Logic:**
1. Try static route match first (O(1) lookup)
2. If no static match, iterate pattern routes sequentially
3. For matched pattern, extract path parameters using regex groups
4. Require namespace and resolve handler var
5. Return handler with metadata and path params

### Module 4: Ring Integration

**File:** `src/esp1/fsr/ring.clj` (extend existing)

```clojure
(defn wrap-compiled-routes
  "Ring middleware that uses compiled routes instead of filesystem.
   Options:
   - :compiled-routes-path - Path to compiled-routes.edn
   - :compiled-routes - Pre-loaded compiled routes data"
  [handler {:keys [compiled-routes-path compiled-routes]}]
  ...)
```

**Logic:**
- Load compiled routes at middleware initialization (not per-request)
- On each request, use `match-route` to find handler
- If found, invoke handler with request + metadata + path-params
- If not found, pass to next middleware
- No filesystem access during request handling

### Module 5: Publishing Integration

**File:** `src/esp1/fsr/static.clj` (extend existing)

```clojure
(defn publish
  "Unified publishing function.
   - Generates static HTML for GET routes (existing behavior)
   - Compiles non-GET routes to compiled-routes.edn (new)
   Options:
   - :root-fs-path - Source route directory
   - :publish-dir - Output directory for static files
   - :compile-routes? - Enable route compilation (default true)
   - :compiled-routes-file - Output path (default: publish-dir/compiled-routes.edn)"
  [options]
  ...)
```

**Logic:**
1. Generate static HTML for GET routes (existing `publish-static`)
2. If `:compile-routes?` is true:
   - Discover all non-GET routes
   - Compile to data structure
   - Write to `:compiled-routes-file`
3. Return summary of generated files + compiled routes

## Key Algorithms

### Pattern Route Matching

```clojure
(defn match-pattern-route [uri method pattern-routes]
  (reduce
    (fn [_ route]
      (when-let [handler-info (get-in route [:methods method])]
        (when-let [matches (re-matches (:pattern route) uri)]
          (let [param-values (rest matches) ; first match is full string
                path-params (zipmap (:param-names route) param-values)]
            (reduced
              {:handler-var (require-and-resolve (:ns handler-info) (:handler handler-info))
               :path-params path-params
               :metadata (:metadata handler-info)})))))
    nil
    pattern-routes))
```

**Complexity:**
- Static routes: O(1) hash lookup
- Pattern routes: O(n) where n = number of pattern routes
- Regex matching: O(m) where m = URI length
- Overall: O(1) for static, O(n*m) for patterns

**Optimization opportunities:**
- Prefix tree for common route prefixes
- Cache compiled regex patterns
- Sort patterns by specificity

### Handler Resolution with Delegation

```clojure
(defn resolve-handler-chain
  "Follows :endpoint/type delegation to find concrete handler.
   Returns: Final handler info with resolved metadata chain."
  [ns-sym http-method]
  (loop [current-ns ns-sym
         metadata-chain []]
    (require current-ns)
    (let [ns-meta (ns-endpoint-meta current-ns)
          combined-meta (apply merge (conj metadata-chain ns-meta))]
      (if-let [handler-sym (get-in ns-meta [:endpoint/http http-method])]
        {:handler handler-sym
         :ns current-ns
         :metadata combined-meta}
        (if-let [type-sym (:endpoint/type ns-meta)]
          (recur type-sym (conj metadata-chain ns-meta))
          nil))))) ; No handler found in chain
```

## Performance Characteristics

### Build Time (Compilation)
- Route discovery: O(f) where f = number of `.clj` files
- Metadata loading: O(f * avg-file-size)
- Compilation: O(r) where r = number of routes
- Serialization: O(r * avg-metadata-size)

**Expected:** < 1 second for 1000 routes

### Runtime (Matching)
- Static route: O(1) hash lookup + O(1) method lookup
- Pattern route: O(p * m) where p = pattern routes, m = URI length
- Handler resolution: O(1) require + O(1) var lookup
- Total: O(1) for static, O(p*m) for patterns

**Expected:** < 1ms static, < 5ms pattern (for reasonable p)

### Memory
- Static route: ~100 bytes per route (URI string + handler info)
- Pattern route: ~200 bytes per route (regex + metadata)
- Total: ~10KB per 100 routes

## Integration Points

### With Existing Code

1. **core.clj** ([core-module.md](core-module.md))
   - Reuse `filename-match-info` for pattern generation
   - Reuse `ns-endpoint-meta` for metadata extraction
   - Reuse `http-endpoint-fn` for handler resolution

2. **static.clj** (current [static-site-generation](../spec/static-site-generation.md))
   - Extend `publish-static` to call compilation
   - OR create new `publish` that does both
   - Share route discovery logic

3. **ring.clj**
   - New `wrap-compiled-routes` middleware
   - Alternative to `wrap-fs-router` for production
   - Same request enhancement (`:endpoint/ns`, `:endpoint/path-params`)

### Build Tool Integration

```clojure
;; Build script example
(ns my-app.build
  (:require [esp1.fsr.static :refer [publish]]))

(defn -main []
  (publish {:root-fs-path "src/my_app/routes"
            :publish-dir "dist"
            :compile-routes? true})
  (println "Published to dist/"))
```

### Production Server Example

```clojure
(ns my-app.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [esp1.fsr.ring :refer [wrap-compiled-routes]]))

(defn app [request]
  {:status 404 :body "Not found"})

(defn -main []
  (let [handler (wrap-compiled-routes app
                  {:compiled-routes-path "dist/compiled-routes.edn"})]
    (run-jetty handler {:port 3000})))
```

## Migration Path

### Phase 1: Implement Basic Compilation
- Add `src/esp1/fsr/compile.clj`
- Implement route discovery and classification
- Implement static route compilation only
- Add tests with simple static routes

### Phase 2: Add Pattern Support
- Implement pattern route compilation
- Add parameter extraction logic
- Test with `<param>` and `<<param>>` routes
- Verify against existing filesystem behavior

### Phase 3: Runtime Matching
- Add `src/esp1/fsr/runtime.clj`
- Implement compiled route matching
- Add Ring middleware
- Test complete request flow

### Phase 4: Integration & Publishing
- Extend `publish-static` with compilation
- Add build script examples
- Document deployment workflow
- Performance testing & optimization

### Phase 5: Advanced Features (Optional)
- Reitit compilation mode
- Route compilation for custom templates
- Compilation validation tools

## Testing Strategy

### Unit Tests
- Route classification (static vs pattern)
- Pattern generation from URIs
- Handler metadata extraction
- Serialization round-trip

### Integration Tests
- Full compilation of test routes
- Runtime matching against compiled routes
- Comparison: filesystem vs compiled behavior
- Handler invocation with path params

### Performance Tests
- Compilation time for large route sets
- Matching latency (static vs pattern)
- Memory footprint measurement
- Cache effectiveness

## Error Handling

### Compilation Errors
- **Missing namespace**: Warn and skip route
- **Invalid metadata**: Log error with file path
- **Serialization failure**: Fail build with clear message

### Runtime Errors
- **Handler not found**: Return 404
- **Handler throws exception**: Propagate to Ring error handler
- **Invalid compiled routes**: Fail fast at startup with validation

## Observability

### Compilation Logging
```
Compiling routes from src/my_app/routes...
  Found 47 routes (32 static, 15 pattern)
  Compiled 32 static routes
  Compiled 15 pattern routes (42 total methods)
  Wrote dist/compiled-routes.edn (8.2 KB)
```

### Runtime Metrics (optional)
- Route match latency histogram
- Static vs pattern match ratio
- Cache hit rate (if caching added)
- Handler execution time

## Security Considerations

1. **Handler Visibility**: Only compile routes with explicit `:endpoint/http` metadata
2. **Code Injection**: Compiled routes use symbols, not strings-as-code
3. **Path Traversal**: Pattern matching confined to declared parameters
4. **Resource Exhaustion**: Limit number of pattern routes (or optimize matching)

## Future Enhancements

### Potential Optimizations
1. **Prefix Tree**: Group routes by common prefix for faster pattern matching
2. **Static Regex Compilation**: Pre-compile regex patterns at load time
3. **Method Indexing**: Separate pattern routes by HTTP method
4. **Lazy Namespace Loading**: Only require namespaces when routes are first hit

### Reitit Integration
For complex routing needs, compile to Reitit format:

```clojure
{:reitit-routes
 [["/api/users" {:post {:handler #'my-app.routes.api.users/create-user}}]
  ["/thing/:id" {:get {:handler #'my-app.routes.thing.<id>/get-thing}}]]}
```

Enable with `:compiler :reitit` option.

## References

- [Production Route Compilation Spec](../spec/compiled-route-production.md)
- [Core Module Implementation](core-module.md)
- [Static Site Generation Spec](../spec/static-site-generation.md)
- [URI to File Routing Spec](../spec/uri-to-file-routing.md)
