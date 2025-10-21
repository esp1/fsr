# Feature Specification: Ring Middleware Integration

## Overview

The Ring Middleware Integration feature provides seamless integration with Ring-based Clojure web applications, enabling fsr to function as standard Ring middleware with support for hot-reloading during development.

## User Value

Developers can easily add filesystem routing to existing Ring applications with a single middleware wrapper, and benefit from automatic code reloading during development without restarting the server.

## User Stories

### Primary Story
**As a** Ring application developer
**I want** to add fsr as middleware to my Ring handler stack
**So that** I can use filesystem routing without changing my application architecture

### Supporting Stories

1. **Simple Integration**
   - **As a** developer
   - **I want** a single `wrap-fs-router` function that wraps my handler
   - **So that** integration requires minimal code changes

2. **Hot Reload in Development**
   - **As a** developer
   - **I want** my route handlers to automatically reload when I save files
   - **So that** I can see changes immediately without restarting my server

3. **Response Type Flexibility**
   - **As a** developer
   - **I want** to return strings, Ring maps, or nil from my handlers
   - **So that** I can use the most convenient format for each route

## Functional Requirements

### FR-001: Middleware Wrapper Function
The system MUST provide a `wrap-fs-router` function that wraps Ring handlers and intercepts requests for filesystem routing.

**Acceptance Criteria**:
- Function signature: `(wrap-fs-router handler root-fs-path)`
- `handler` is the next middleware in the chain (for fallthrough)
- `root-fs-path` is a string path to the root directory for routes
- Returns a new handler function that accepts Ring request maps

Example:
```clojure
(def app
  (-> handler
      (wrap-fs-router "src/my_app/routes")))
```

### FR-002: Request Interception
The middleware MUST intercept incoming requests and attempt to resolve them using fsr routing before falling through to the wrapped handler.

**Acceptance Criteria**:
- If fsr finds a matching route, it handles the request and returns the response
- If fsr does not find a matching route, the request is passed to the wrapped handler
- The wrapped handler can implement 404 handling or other fallback logic
- Request URI is extracted from the Ring request map's `:uri` key

### FR-003: String Response Normalization
The middleware MUST convert string responses from handlers into proper Ring response maps with HTTP 200 status.

**Acceptance Criteria**:
- String return values become `{:status 200, :body "<string>", :headers {"Content-Type" "text/html"}}`
- Empty strings are valid and result in HTTP 200 with empty body
- Content-Type defaults to "text/html"

### FR-004: Nil Response Normalization
The middleware MUST convert nil responses from handlers into Ring response maps with HTTP 204 No Content status.

**Acceptance Criteria**:
- `nil` return values become `{:status 204, :body ""}`
- HTTP 204 indicates success with no content to return
- No Content-Type header is set for 204 responses

### FR-005: Ring Response Map Pass-through
The middleware MUST pass through Ring response maps from handlers without modification.

**Acceptance Criteria**:
- Handlers can return maps with `:status`, `:headers`, and `:body` keys
- The middleware does not modify these maps
- Handlers have full control over response status codes and headers

### FR-006: Hot Reload Support
The middleware MUST support hot-reloading of route namespaces in development mode when tools.namespace is available.

**Acceptance Criteria**:
- When tools.namespace is on the classpath, route cache is cleared on each request
- Modified route files are automatically reloaded
- Handlers see updated code without server restart
- In production (without tools.namespace), caching is preserved

### FR-007: Request Map Enrichment
The middleware MUST enrich the request map with fsr-specific keys before passing it to handlers.

**Acceptance Criteria**:
- `:endpoint/ns` contains the matched namespace symbol
- `:endpoint/path-params` contains extracted path parameters (or empty map)
- All namespace metadata is merged into the request
- Original Ring request keys are preserved

### FR-008: Error Handling
The middleware MUST handle errors gracefully and provide clear error messages for common problems.

**Acceptance Criteria**:
- Missing handler functions result in clear error messages
- Invalid namespace structures are reported with context
- Errors include the URI and namespace being processed
- Stack traces are preserved for debugging

## Non-Functional Requirements

### Performance
- Middleware overhead should be minimal (< 1ms per request)
- Route caching should prevent repeated filesystem scans
- Hot-reload overhead is acceptable in development but avoided in production

### Compatibility
- Must work with standard Ring middleware patterns
- Should integrate with common Ring middleware (wrap-params, wrap-session, etc.)
- Compatible with Ring 1.x specification

### Developer Experience
- Integration should require minimal configuration
- Error messages should be actionable
- Hot-reload should be automatic with no manual cache clearing

## Edge Cases

1. **No Matching Route**: Request passes through to wrapped handler
2. **Handler Throws Exception**: Exception propagates to Ring error handling
3. **Invalid Root Path**: Clear error on middleware initialization
4. **Concurrent Requests During Reload**: Cache invalidation is thread-safe
5. **Missing tools.namespace**: Hot-reload gracefully disabled without error

## Middleware Ordering

fsr middleware should typically be placed:
- **After** authentication/session middleware (routes can access `:session`, etc.)
- **After** parameter parsing middleware (routes can access `:params`)
- **Before** default 404 handlers
- **Before** error handling middleware (to allow route errors to be caught)

Example:
```clojure
(def app
  (-> handler
      wrap-error-handling
      (wrap-fs-router "src/routes")
      wrap-params
      wrap-session))
```

## Development vs Production

### Development Mode
- tools.namespace on classpath → hot-reload enabled
- Route cache cleared on each request
- Modified files automatically reloaded
- Slower but convenient for rapid iteration

### Production Mode
- tools.namespace not on classpath → caching enabled
- Routes resolved once and cached
- Faster request handling
- Requires restart to see changes

## Integration Example

```clojure
(ns my-app.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.params :refer [wrap-params]]
            [esp1.fsr.ring :refer [wrap-fs-router]]))

(defn not-found-handler [request]
  {:status 404
   :body "<h1>Page Not Found</h1>"})

(def app
  (-> not-found-handler
      (wrap-fs-router "src/my_app/routes")
      wrap-params))

(defn -main []
  (run-jetty app {:port 3000}))
```

## Dependencies

- Requires URI to File Routing for route resolution
- Requires Namespace Metadata System for handler discovery
- Integrates with Route Caching for performance
- Optional dependency on tools.namespace for hot-reload

## Related Requirements

- [URI to File Routing](uri-to-file-routing.md) - Describes route resolution
- [Namespace Metadata System](namespace-metadata.md) - Describes handler configuration
- [Route Caching](route-caching.md) - Describes performance optimization
