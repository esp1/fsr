# Technical Architecture

## Overview

fsr is a Clojure library that provides filesystem-based routing for web applications. The architecture is intentionally minimal, consisting of four core modules that work together to map HTTP requests to Clojure namespace handlers.

## Technology Stack

- **Language**: Clojure
- **Core Dependencies**: None (zero-dependency principle)
- **Development Dependencies**:
  - tools.namespace (hot-reload support)
  - Malli (schema validation)
  - Codox (API documentation)
- **Integration**: Ring specification for middleware

## System Architecture

### High-Level Component Structure

```
┌─────────────────────────────────────────────────────┐
│                   Ring Application                   │
└──────────────────┬──────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────┐
│           wrap-fs-router (ring.clj)                 │
│  • Request interception                              │
│  • Hot-reload coordination (dev mode)                │
└──────────────────┬──────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────┐
│           Route Resolution (core.clj)                │
│  • URI to file mapping                               │
│  • Path parameter extraction                         │
│  • Namespace metadata resolution                     │
└──────────────────┬──────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────┐
│           Route Cache (cache.clj)                    │
│  • Resolution caching                                │
│  • Performance optimization                          │
└─────────────────────────────────────────────────────┘
```

### Core Modules

#### 1. Core Module (src/esp1/fsr/core.clj)
[See technical details: tech/core-module.md](tech/core-module.md)

The heart of fsr's routing logic:
- URI to filesystem path resolution
- Path parameter extraction and matching
- Namespace metadata resolution
- Handler function discovery
- Route cache coordination

**Key Functions**:
- `uri->file` - Maps URIs to .clj files
- `get-root-ns-prefix` - Determines namespace prefix from filesystem
- `step-match` - Pattern matching for URI segments
- `resolve-handler` - Locates handler functions via metadata

#### 2. Ring Integration Module (src/esp1/fsr/ring.clj)
[See technical details: tech/ring-integration.md](tech/ring-integration.md)

Ring middleware implementation:
- Request/response lifecycle management
- Hot-reload support (development mode)
- Handler invocation with enriched request maps
- Response normalization (string/map/nil handling)

**Key Functions**:
- `wrap-fs-router` - Main middleware wrapper
- `handle-request` - Request processing logic

#### 3. Cache Module (src/esp1/fsr/cache.clj)
[See technical details: tech/cache-module.md](tech/cache-module.md)

Performance optimization through intelligent caching:
- In-memory route resolution caching
- Cache invalidation strategies
- Development vs production cache behavior

**Key Data Structures**:
- `route-cache` - Atom storing URI to handler mappings

#### 4. Static Site Generation Module (src/esp1/fsr/static.clj)
[See technical details: tech/static-generation.md](tech/static-generation.md)

Static site generation capabilities:
- Route discovery and enumeration
- Handler invocation for GET methods
- URI tracking for dynamic parameters
- File output and organization

**Key Functions**:
- `publish-static` - Main generation entry point
- `track-uri` - Dynamic URI registration

#### 5. Route Compilation Module (src/esp1/fsr/compile.clj)
[See technical details: tech/route-compilation.md](tech/route-compilation.md)

Production route compilation for optimized deployment:
- Compilation of non-GET routes to efficient data structures
- Static vs pattern route classification
- Serialization to EDN format
- Runtime route matching without filesystem access

**Key Functions**:
- `compile-routes` - Main compilation entry point
- `discover-routes` - Route discovery and classification
- `compile-static-route` - Static route compilation
- `compile-pattern-route` - Parameterized route compilation

#### 6. Runtime Matching Module (src/esp1/fsr/runtime.clj)
[See technical details: tech/route-compilation.md#module-3-runtime-matching](tech/route-compilation.md)

Production runtime for compiled routes:
- Loading compiled routes from EDN
- Efficient route matching (static O(1), pattern O(n))
- Handler resolution and invocation
- Ring middleware integration

**Key Functions**:
- `load-compiled-routes` - Load compiled routes at startup
- `match-route` - Route matching without filesystem
- `wrap-compiled-routes` - Production Ring middleware

## Design Principles

### 1. Zero External Dependencies
The core library has no runtime dependencies beyond Clojure itself. This ensures:
- Minimal footprint
- No version conflicts
- Easy integration into any project
- Long-term stability

### 2. Convention Over Configuration
- Filesystem structure directly maps to URI structure
- Namespace metadata provides declarative configuration
- Sensible defaults reduce boilerplate

### 3. Separation of Concerns
- `core.clj` - Pure routing logic (no Ring coupling)
- `ring.clj` - Ring-specific integration
- `static.clj` - Static generation (separate concern)
- `cache.clj` - Performance optimization (isolated)

### 4. Development Experience
- Hot-reload support via tools.namespace
- Clear error messages for common mistakes
- Flexible enough for custom templates

## Data Flow

### Dynamic Request Handling

1. **Request Arrives**: Ring request enters `wrap-fs-router` middleware
2. **Cache Check**: Consult route cache for previously resolved routes
3. **Route Resolution**: If cache miss, use `core.clj` to resolve URI to namespace
4. **Parameter Extraction**: Extract path parameters from URI pattern matching
5. **Metadata Resolution**: Load namespace metadata to find handler function
6. **Request Enrichment**: Merge metadata and add `:endpoint/ns`, `:endpoint/path-params`
7. **Handler Invocation**: Call resolved handler with enriched request
8. **Response Normalization**: Convert handler response to Ring format
9. **Cache Update**: Store resolution in cache for future requests

### Static Generation Flow

1. **Route Discovery**: Scan filesystem for all .clj files under root path
2. **Endpoint Filtering**: Identify GET handlers via namespace metadata
3. **URI Enumeration**: Generate URIs for parameter-free routes + tracked dynamic URIs
4. **Handler Invocation**: Call each handler with synthetic request
5. **Content Generation**: Capture handler response
6. **File Output**: Write content to output directory in URI structure

## Key Algorithms

### URI to File Matching Algorithm

The core matching algorithm in `core.clj` uses a recursive step-by-step approach:

1. Split URI into segments
2. For each segment:
   - Check for exact filename match (with underscore conversion)
   - Check for pattern match with `<param>` or `<<param>>`
   - If no match and last segment, try `index.clj`
3. Accumulate path parameters during traversal
4. Return matched file + parameters map

### Path Parameter Extraction

Two parameter types are supported:
- `<param>` - Matches `[^/]*` (any characters except slash)
- `<<param>>` - Matches `.*` (any characters including slash)

The regex patterns are constructed in `filename-match-info` function.

## Performance Considerations

### Route Cache Strategy
[See technical details: tech/cache-module.md](tech/cache-module.md)

- Cache key: URI string
- Cache value: Resolution result (namespace, handler, parameters)
- Development: Cache cleared on each request (hot-reload)
- Production: Cache persists for application lifetime

### Namespace Loading
- Namespaces loaded lazily on first request
- Metadata cached after initial load
- Hot-reload in dev mode via tools.namespace

## Testing Strategy
[See technical details: tech/testing-strategy.md](tech/testing-strategy.md)

- Unit tests for core routing logic
- Example routes in `test/` directory demonstrate functionality
- Malli generative testing for schema validation
- Integration tests via test Ring application

## Deployment Considerations

### Development Mode
- Deploy as standard Ring application
- Use `wrap-fs-router` middleware
- Configure root filesystem path for routes
- Enable hot-reload for rapid iteration
- Route resolution via filesystem scanning

### Production Mode (Static + Compiled)
[See technical details: tech/route-compilation.md](tech/route-compilation.md)

**Build Phase**:
1. Run `publish` to generate both static HTML and compiled routes
2. GET routes → Static HTML files in publish directory
3. Non-GET routes → `compiled-routes.edn` data structure

**Deployment**:
- Deploy static HTML files to CDN/static hosting
- Deploy compiled routes + handler code to application server
- No source `.clj` files needed in production
- Use `wrap-compiled-routes` middleware instead of `wrap-fs-router`
- Zero filesystem scanning at runtime

**Benefits**:
- Faster startup (routes pre-compiled)
- Predictable performance (no cache warming)
- Smaller deployment artifact
- Improved security (no source code exposure)

## Extension Points

### Custom Templates
Developers can create custom template namespaces using `:endpoint/type` metadata delegation. The template namespace receives the original matched namespace via `:endpoint/ns` in the request map.

### Custom Response Handlers
The system supports three response types:
- String → HTTP 200 with string body
- Ring response map → Pass through
- nil → HTTP 204 No Content

Custom middleware can wrap handlers to support additional response types.

## Related Documentation

- [Project Requirements](PROJECT_REQUIREMENTS.md) - High-level feature requirements
- [Core Module Details](tech/core-module.md) - Deep dive into routing logic
- [Ring Integration](tech/ring-integration.md) - Middleware implementation
- [Cache System](tech/cache-module.md) - Caching strategy and implementation
- [Static Generation](tech/static-generation.md) - Static site generation details
- [Route Compilation](tech/route-compilation.md) - Production route compilation system
