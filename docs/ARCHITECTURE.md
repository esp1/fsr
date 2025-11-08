# Technical Architecture

## Stack

- **Language**: Clojure
- **Core**: Zero dependencies
- **Dev**: tools.namespace, Malli, Codox
- **Integration**: Ring middleware

## Modules

1. **core.clj** - URI→file resolution, path parameters, namespace metadata
   [tech/core-module.md](tech/core-module.md)

2. **ring.clj** - Ring middleware, hot-reload support
   [tech/ring-integration.md](tech/ring-integration.md)

3. **cache.clj** - Route caching for performance
   [tech/cache-module.md](tech/cache-module.md)

4. **static.clj** - Static site generation from GET endpoints
   [tech/static-generation.md](tech/static-generation.md)

5. **compile.clj** - Compile routes to EDN for production
   [tech/route-compilation.md](tech/route-compilation.md)

6. **runtime.clj** - Load/match compiled routes (zero filesystem access)
   [tech/route-compilation.md](tech/route-compilation.md)

## Design Principles

- **Zero dependencies**: Core has no runtime deps beyond Clojure
- **Convention over config**: Filesystem structure = URI structure
- **Separation of concerns**: Pure routing (core.clj), Ring integration (ring.clj), caching (cache.clj)
- **Dev experience**: Hot-reload, clear errors, custom template support

## Request Flow

**Development**: Request → Ring middleware → Cache check → Route resolution (core.clj) → Handler invocation → Response

**Production**: Request → `wrap-compiled-routes` → Load EDN routes → Match (O(1) static, O(n) pattern) → Handler → Response

## Key Algorithms

**URI matching** (core.clj): Split URI into segments, match exact files or `<param>`/`<<param>>` patterns, accumulate parameters, return matched namespace + params.

**Caching**: URI → resolution result. Dev: cleared each request. Production: persists for app lifetime.

## Deployment

**Dev**: Use `wrap-fs-router`, enable hot-reload, routes resolved from filesystem

**Prod**: Run `publish` → static HTML + `compiled-routes.edn`, use `wrap-compiled-routes`, zero filesystem access
[tech/route-compilation.md](tech/route-compilation.md)

## Extension

**Custom templates**: Use `:endpoint/type` metadata to delegate to template namespaces

**Response types**: String (200), Ring map (passthrough), nil (204)
