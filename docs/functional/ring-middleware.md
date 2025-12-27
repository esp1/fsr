# Ring Middleware

## Purpose

Integrate fsr with Ring-based web applications via standard middleware.

## Value

Add filesystem routing with one wrapper function. Hot-reload in dev, cached performance in production.

## Requirements

**FR-001: Middleware Wrapper** - `wrap-fs-router` wraps Ring handlers:
```clojure
(-> handler (wrap-fs-router "src/my_app/routes"))
```

**FR-002: Request Interception** - Matching route handles request; no match passes to wrapped handler.

**FR-003: String Response** - String return → `{:status 200, :body "<string>", :headers {"Content-Type" "text/html"}}`.

**FR-004: Nil Response** - `nil` return → `{:status 204, :body ""}`.

**FR-005: Ring Map Passthrough** - Ring response maps returned unchanged.

**FR-006: Hot Reload** - When `tools.namespace` on classpath, cache clears each request for immediate code changes.

**FR-007: Request Enrichment** - Request map includes:
- `:endpoint/ns` - matched namespace symbol
- `:endpoint/path-params` - extracted parameters (or empty map)
- All namespace metadata merged in

**FR-008: Error Handling** - Missing handlers, invalid namespaces reported with URI context.

## Middleware Ordering

```clojure
(-> handler
    wrap-error-handling      ; outer
    (wrap-fs-router "src/routes")
    wrap-params
    wrap-session)            ; inner
```

Place fsr:
- After auth/session/params (routes can access `:session`, `:params`)
- Before 404 handlers and error handling

## Dev vs Production

| Mode | Detection | Behavior |
|------|-----------|----------|
| Development | `tools.namespace` on classpath | Cache cleared per request, hot-reload |
| Production | `tools.namespace` absent | Cache persists, fast lookups |

## Edge Cases

- No matching route → passes to wrapped handler
- Handler throws → propagates to Ring error handling
- Invalid root path → error on initialization
- Missing `tools.namespace` → hot-reload disabled silently

## Related

- [uri-to-file-routing.md](uri-to-file-routing.md) - Route resolution
- [namespace-metadata.md](namespace-metadata.md) - Handler configuration
- [route-caching.md](route-caching.md) - Performance optimization
