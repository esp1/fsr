# Route Compilation

**Modules**: `esp1.fsr.compile` + `esp1.fsr.runtime`

Implements production route compilation. See [requirements](../functional/compiled-route-production.md).

## Architecture

**Build Time** (compile.clj):
```
Filesystem → Discover routes → Extract metadata → Classify → Compile → Write EDN
```

**Runtime** (runtime.clj):
```
Request → Load EDN (startup) → Match route → Resolve handler → Execute
```

## Compiled Route Format

```clojure
{:static-routes
 {"/api/users" {:methods {:post {:ns 'app.routes.api.users
                                 :handler 'create-user}}}}

 :pattern-routes
 [{:pattern #"/thing/([^/]+)"
   :uri-template "/thing/<id>"
   :param-names ["id"]
   :methods {:get {:ns 'app.routes.thing.<id>
                   :handler 'get-thing}}}]}
```

**Design**: Static routes use O(1) hash lookup. Pattern routes use sequential regex matching (preserves priority).

## Key Functions

### compile.clj

**`discover-routes`** - Scan filesystem, return route metadata.

**`classify-route`** - Determine `:static` or `:pattern` based on URI.

**`compile-routes`** - Main entry point. Returns `{:static-routes {...} :pattern-routes [...]}`.

### runtime.clj

**`load-compiled-routes`** - Read EDN file, return data structure.

**`match-route`** - Try static lookup first, then pattern matching.

**`wrap-compiled-routes`** - Ring middleware using compiled routes.

## Pattern Matching

```clojure
(defn match-pattern-route [uri method pattern-routes]
  (reduce
    (fn [_ route]
      (when-let [matches (re-matches (:pattern route) uri)]
        (when-let [handler-info (get-in route [:methods method])]
          (reduced
            {:handler-var (resolve-handler (:ns handler-info) (:handler handler-info))
             :path-params (zipmap (:param-names route) (rest matches))}))))
    nil
    pattern-routes))
```

**Complexity**: Static O(1), Pattern O(n×m) where n=patterns, m=URI length.

## Handler Resolution with Delegation

Follows `:endpoint/type` chains to find concrete handler:

```clojure
(defn resolve-handler-chain [ns-sym http-method]
  (loop [current-ns ns-sym, metadata-chain []]
    (require current-ns)
    (let [ns-meta (ns-endpoint-meta current-ns)]
      (if-let [handler-sym (get-in ns-meta [:endpoint/http http-method])]
        {:handler handler-sym, :ns current-ns, :metadata (apply merge metadata-chain ns-meta)}
        (when-let [type-sym (:endpoint/type ns-meta)]
          (recur type-sym (conj metadata-chain ns-meta)))))))
```

## Static HTML Compilation

**Root Index Handling**: Empty URIs (from `ns-sym->uri` for root `index.clj`) normalized to `"index"` before resolution.

**Tracked URI Normalization**: Leading slashes stripped from tracked URIs before resolution.

## Ring Integration

```clojure
(defn wrap-compiled-routes [handler {:keys [compiled-routes-path]}]
  (let [routes (load-compiled-routes compiled-routes-path)]
    (fn [request]
      (if-let [match (match-route (:uri request) (:request-method request) routes)]
        (invoke-handler match request)
        (handler request)))))
```

## Performance

| Phase | Complexity | Expected |
|-------|------------|----------|
| Compilation | O(files) | < 1s for 1000 routes |
| Static match | O(1) | < 1ms |
| Pattern match | O(patterns) | < 5ms |
| Memory | O(routes) | ~10KB per 100 routes |

## Error Handling

**Compilation**: Missing namespace → warn and skip. Invalid metadata → log with file path.

**Runtime**: Handler not found → 404. Handler throws → propagate to Ring error handler.

## Related

- [Production Route Compilation Spec](../functional/compiled-route-production.md) - Requirements
- [Core Module](core-module.md) - Reused functions
- [Static Site Generation Spec](../functional/static-site-generation.md) - GET route handling
