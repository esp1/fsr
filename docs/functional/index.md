# Functional Requirements

## Vision

Filesystem-based routing for Clojure: URIs map to .clj files via directory structure. Zero dependencies, supports dynamic apps and static sites.

## Features

1. **URI to File Routing** - Maps URIs to .clj files via filesystem structure
   [uri-to-file-routing.md](uri-to-file-routing.md)

2. **Path Parameters** - Extract dynamic values: `<param>` (no slashes), `<<param>>` (with slashes)
   [uri-to-file-routing.md](uri-to-file-routing.md)

3. **Namespace Metadata** - Configure handlers via `:endpoint/http` and `:endpoint/type` metadata
   [namespace-metadata.md](namespace-metadata.md)

4. **Ring Middleware** - Integrates with Ring apps, supports hot-reload in dev
   [ring-middleware.md](ring-middleware.md)

5. **Static Site Generation** - Generate static HTML from GET endpoints
   [static-site-generation.md](static-site-generation.md)

6. **Production Compilation** - Compile routes to static HTML + EDN for zero-filesystem runtime
   [compiled-route-production.md](compiled-route-production.md)

7. **Route Caching** - Cache resolution results for performance
   [route-caching.md](route-caching.md)

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
