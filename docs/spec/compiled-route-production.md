# Production Route Compilation

## Overview

Support efficient production deployment by compiling filesystem routes into optimized artifacts:
- **GET requests** → pre-rendered static HTML files (existing behavior)
- **Non-GET requests** → compiled route lookup data structures (new capability)

This enables zero-runtime filesystem scanning while preserving all dynamic routing capabilities.

## User Value

When publishing a site to production, developers need:
- **Static content** (GET requests) rendered to HTML files for CDN serving
- **Dynamic behavior** (POST/PUT/DELETE/etc.) compiled to efficient route lookups for server handling
- **Zero runtime filesystem scanning** for performance and security
- **Predictable resource requirements** for deployment planning
- **Minimal deployment artifacts** (no source `.clj` files needed)

## Current Behavior

The existing `publish-static` function (see [Static Site Generation](static-site-generation.md)) handles GET requests by:
1. Discovering all GET handlers via filesystem scanning
2. Invoking handlers with synthetic requests
3. Writing response bodies to HTML files
4. Supporting `track-uri` for parameterized routes

## Gap: Non-GET Routes

Non-GET HTTP methods (POST, PUT, DELETE, etc.) are not handled during publishing, which means production deployments must:
- Include source `.clj` files
- Perform runtime filesystem scanning
- Accept cache warming latency
- Have unpredictable first-request performance

## Feature Requirements

### FR1: Compile Non-GET Routes

The system shall compile all non-GET route handlers into an efficient lookup data structure during the publish phase.

**Acceptance Criteria:**
- All routes with POST, PUT, DELETE, PATCH, HEAD, OPTIONS handlers are identified
- Handler function references are preserved in compiled output
- Path parameter extraction patterns are included in compiled routes
- Compiled routes can be serialized to EDN for deployment

### FR2: Support Route Types

The compilation system shall handle three types of routes with appropriate optimization:

1. **Static routes** (no parameters): `/api/users`, `/api/posts`
   - Direct map lookup (fastest)

2. **Parameterized routes**: `/thing/<id>`, `/docs/<<path>>`
   - Pattern matching with parameter extraction

3. **Mixed routes**: `/api/users/<id>/posts`
   - Hybrid approach: map lookup prefix + pattern matching suffix

**Acceptance Criteria:**
- Static routes compile to simple hash map lookups
- Parameterized routes compile with regex patterns and parameter names
- Performance characteristics are documented for each type

### FR3: Preserve Handler Metadata

The compilation process shall preserve all namespace metadata required for handler execution.

**Acceptance Criteria:**
- Handler function var references are maintained
- `:endpoint/ns` information is included
- `:endpoint/type` delegation chains are resolved
- Path parameter names and patterns are preserved

### FR4: Optional Reitit Integration

For complex routing scenarios, the system shall support compilation to Reitit route data structures as an opt-in feature.

**Acceptance Criteria:**
- A configuration flag enables Reitit compilation mode
- Compiled routes conform to Reitit data structure format
- Documentation explains when to use Reitit vs built-in compilation

### FR5: Production Runtime

The compiled routes shall be loadable and executable in production without filesystem access.

**Acceptance Criteria:**
- Compiled routes can be loaded from EDN file
- Route matching works with only compiled data (no filesystem reads)
- Performance is equal to or better than runtime filesystem scanning
- Memory footprint is predictable and documented

## Use Cases

### UC1: API-Heavy Application

**Scenario:** A web application with many static pages (blog posts, documentation) and a REST API for user interactions.

**Flow:**
1. Developer runs publish with route compilation enabled
2. GET requests → HTML files in `publish-dir/`
3. POST/PUT/DELETE routes → `compiled-routes.edn` file
4. Production server loads compiled routes at startup
5. API requests use compiled route lookup (no filesystem access)
6. Static pages served directly by CDN/nginx

### UC2: Incremental Migration

**Scenario:** Gradually moving from dev mode to production deployment.

**Flow:**
1. Start with full filesystem routing in development
2. Test static site generation for GET routes
3. Add route compilation for non-GET routes
4. Validate compiled routes match filesystem behavior
5. Deploy with compiled routes, filesystem as fallback
6. Monitor performance, then remove filesystem fallback

### UC3: Embedded/Constrained Deployment

**Scenario:** Deploying to environment where filesystem access is restricted (containers, serverless, etc.).

**Flow:**
1. Compile all routes during build phase
2. Package only compiled routes + handler code (no `.clj` source files)
3. Deploy minimal artifact
4. Runtime loads compiled routes from embedded resource

## Non-Goals

- **Build-time request execution**: Unlike GET routes that are rendered at build time, non-GET routes are only compiled (not executed)
- **Request validation**: Parameter validation happens at runtime, not compile time
- **Hot reloading in production**: Compiled routes are static; changes require recompilation
- **Automatic route versioning**: Version management is a separate concern

## Success Metrics

- Compilation time: < 1 second per 100 routes
- Runtime lookup: < 1ms per request (static routes), < 5ms (parameterized routes)
- Memory overhead: < 10KB per route in compiled form
- Deployment size reduction: 50%+ (no source `.clj` files needed)

## Related Features

- [Static Site Generation](static-site-generation.md) - Describes GET request rendering (Phase 1)
- [URI to File Routing](uri-to-file-routing.md) - Runtime route resolution logic being compiled
- [Namespace Metadata](namespace-metadata.md) - Metadata preserved in compiled routes

## Relationship to Static Site Generation

This feature **extends** static site generation rather than replacing it:

| Aspect | Static Site Gen (GET) | Route Compilation (Non-GET) |
|--------|----------------------|------------------------------|
| **Output** | HTML files | EDN data structure |
| **When** | Build time | Build time |
| **Runtime** | Files served directly | Routes loaded, matched, executed |
| **Execution** | Handlers invoked at build | Handlers invoked per request |
| **Purpose** | Pre-render content | Pre-compile route lookup |

Both features are part of the **production publishing workflow** and should be invoked together.
