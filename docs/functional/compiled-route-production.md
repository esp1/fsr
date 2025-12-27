# Production Route Compilation

## Purpose

Compile filesystem routes into optimized artifacts for production:
- **GET requests** → pre-rendered static HTML (see [static-site-generation.md](static-site-generation.md))
- **Non-GET requests** → compiled route lookup structures (EDN)

Zero filesystem scanning at runtime.

## Value

- Static content served from CDN
- Dynamic behavior via efficient route lookups
- No source `.clj` files needed in deployment
- Predictable performance and resource usage

## Requirements

**FR-001: Non-GET Compilation** - Compile POST/PUT/DELETE/PATCH/HEAD/OPTIONS routes to EDN lookup structures.

**FR-002: Route Types**
- Static routes (`/api/users`) → hash map lookup (O(1))
- Parameterized routes (`/thing/<id>`) → regex pattern matching
- Mixed routes (`/api/users/<id>/posts`) → hybrid approach

**FR-003: Metadata Preservation** - Handler vars, `:endpoint/ns`, `:endpoint/type` chains, and path parameter patterns preserved.

**FR-004: Production Runtime** - `wrap-compiled-routes` middleware loads EDN at startup, matches routes without filesystem access.

**FR-005: Publishing Integration** - `publish` function generates both static HTML and `compiled-routes.edn`.

## Use Cases

**API Application**: GET → HTML files on CDN; POST/PUT/DELETE → compiled routes on server.

**Constrained Deployment**: Compile all routes at build time, deploy minimal artifact (no `.clj` sources).

## Non-Goals

- Build-time execution of non-GET handlers (only compiled, not invoked)
- Request validation at compile time
- Hot reloading in production
- Automatic versioning

## Relationship to Static Site Generation

| Aspect | Static (GET) | Compiled (Non-GET) |
|--------|--------------|-------------------|
| Output | HTML files | EDN data |
| Runtime | Files served directly | Routes matched, handlers invoked |
| Handler execution | At build time | At request time |

Both are part of the production publishing workflow.

## Related

- [static-site-generation.md](static-site-generation.md) - GET request rendering
- [uri-to-file-routing.md](uri-to-file-routing.md) - Route resolution logic
- [namespace-metadata.md](namespace-metadata.md) - Metadata in compiled routes
- [../technical/route-compilation.md](../technical/route-compilation.md) - Implementation
