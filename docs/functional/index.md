# Functional Requirements

## Vision

Filesystem-based routing for Clojure: URIs map to .clj files via directory structure. Zero dependencies, supports dynamic apps and static sites.

## Features

- **[URI to File Routing](uri-to-file-routing.md)** - Maps URIs to .clj files, path parameters (`<param>`, `<<param>>`)
- **[Namespace Metadata](namespace-metadata.md)** - Configure handlers via `:endpoint/http` and `:endpoint/type`
- **[Ring Middleware](ring-middleware.md)** - Ring integration with hot-reload in dev
- **[Route Caching](route-caching.md)** - Cache resolution results for performance
- **[Static Site Generation](static-site-generation.md)** - Generate static HTML from GET endpoints
- **[Production Compilation](compiled-route-production.md)** - Compile to static HTML + EDN for zero-filesystem runtime
