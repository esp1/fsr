# Project Requirements

## Vision

Filesystem-based routing for Clojure: URIs map to .clj files via directory structure. Zero dependencies, supports dynamic apps and static sites.

## Features

1. **URI to File Routing** - Maps URIs to .clj files via filesystem structure
   [spec/uri-to-file-routing.md](spec/uri-to-file-routing.md)

2. **Path Parameters** - Extract dynamic values: `<param>` (no slashes), `<<param>>` (with slashes)
   [spec/uri-to-file-routing.md](spec/uri-to-file-routing.md)

3. **Namespace Metadata** - Configure handlers via `:endpoint/http` and `:endpoint/type` metadata
   [spec/namespace-metadata.md](spec/namespace-metadata.md)

4. **Ring Middleware** - Integrates with Ring apps, supports hot-reload in dev
   [spec/ring-middleware.md](spec/ring-middleware.md)

5. **Static Site Generation** - Generate static HTML from GET endpoints
   [spec/static-site-generation.md](spec/static-site-generation.md)

6. **Production Compilation** - Compile routes to static HTML + EDN for zero-filesystem runtime
   [spec/compiled-route-production.md](spec/compiled-route-production.md)

7. **Route Caching** - Cache resolution results for performance
   [spec/route-caching.md](spec/route-caching.md)

## Scope

**In scope**:
- Filesystem-to-URI routing with path parameters
- Ring middleware integration
- Static site generation and route compilation
- Route caching

**Out of scope**:
- Auth, database, sessions, WebSockets
- Templating engines (users provide via namespaces)
- Client-side routing

## Dependencies

- **Core**: None (zero-dependency principle)
- **Dev**: tools.namespace, Malli, Codox
