# Functional Requirements

## Vision

Filesystem-based routing for Clojure: URIs map to .clj files via directory structure. Zero dependencies, supports dynamic apps and static sites.

## Features

1. **[URI to File Routing](uri-to-file-routing.md)** - Maps URIs to .clj files via filesystem structure

2. **[Path Parameters](uri-to-file-routing.md)** - Extract dynamic values: `<param>` (no slashes), `<<param>>` (with slashes)

3. **[Namespace Metadata](namespace-metadata.md)** - Configure handlers via `:endpoint/http` and `:endpoint/type` metadata

4. **[Ring Middleware](ring-middleware.md)** - Integrates with Ring apps, supports hot-reload in dev

5. **[Static Site Generation](static-site-generation.md)** - Generate static HTML from GET endpoints

6. **[Production Compilation](compiled-route-production.md)** - Compile routes to static HTML + EDN for zero-filesystem runtime

7. **[Route Caching](route-caching.md)** - Cache resolution results for performance
