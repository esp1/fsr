# Feature Specification: Namespace Metadata System

## Overview

The Namespace Metadata System enables developers to declaratively configure route handlers using Clojure namespace metadata. This provides a clean, idiomatic way to map HTTP methods to handler functions without requiring external configuration files.

## User Value

Developers can configure their route handlers directly in the namespace declaration where they're defined, keeping configuration close to implementation and leveraging Clojure's built-in metadata capabilities.

## User Stories

### Primary Story
**As a** web application developer
**I want** to declare my HTTP method handlers in namespace metadata
**So that** my route configuration lives alongside my code without external configuration files

### Supporting Stories

1. **HTTP Method Mapping**
   - **As a** developer
   - **I want** to map HTTP methods like GET and POST to specific handler functions
   - **So that** different methods on the same URI can be handled by different functions

2. **Template Delegation**
   - **As a** developer
   - **I want** to use `:endpoint/type` to delegate handling to a template namespace
   - **So that** I can reuse common page layouts across multiple routes

3. **Metadata Access in Handlers**
   - **As a** developer
   - **I want** my namespace metadata merged into the request map
   - **So that** I can pass configuration data from namespace to handler

## Functional Requirements

### FR-001: HTTP Method Handler Mapping
The system MUST resolve handler functions using the `:endpoint/http` key in namespace metadata, which maps HTTP method keywords to function symbols.

**Acceptance Criteria**:
- Namespace metadata contains `:endpoint/http` as a map
- Map keys are lowercase HTTP method keywords (`:get`, `:post`, `:put`, `:delete`, etc.)
- Map values are quoted symbols referring to functions in the same namespace
- The system invokes the function corresponding to the request's HTTP method

Example:
```clojure
(ns my-app.routes.users
  {:endpoint/http {:get 'GET-users
                   :post 'POST-create-user}})
```

### FR-002: Template Type Delegation
The system MUST support `:endpoint/type` metadata that delegates handler resolution to another namespace.

**Acceptance Criteria**:
- `:endpoint/type` value is a quoted namespace symbol
- When `:endpoint/http` is not present or doesn't have a matching method, the system looks up `:endpoint/type`
- The system recursively resolves handlers in the template namespace
- Template namespace receives the original matched namespace via `:endpoint/ns`

Example:
```clojure
(ns my-app.routes.blog.post-1
  {:endpoint/type 'my-app.templates.blog-post
   :blog/title "My First Post"})
```

### FR-003: Request Map Metadata Enrichment
The system MUST merge the matched namespace's metadata into the request map passed to handlers.

**Acceptance Criteria**:
- All namespace metadata keys are available in the request map
- Standard Ring request keys are not overwritten by metadata
- Handlers can access custom metadata keys for configuration
- System-reserved keys (`:endpoint/ns`, `:endpoint/path-params`, `:endpoint/http`, `:endpoint/type`) are handled specially

### FR-004: Matched Namespace Tracking
The system MUST add an `:endpoint/ns` key to the request map containing the symbol of the namespace that matched the original URI.

**Acceptance Criteria**:
- `:endpoint/ns` is always present in handler request maps
- Value is the namespace symbol of the .clj file that matched the URI
- In template scenarios, this is the original matched namespace, not the template namespace
- Handlers can use `(meta (the-ns (:endpoint/ns request)))` to access original namespace metadata

### FR-005: Handler Function Resolution
The system MUST resolve handler function symbols to actual functions using Clojure's namespace resolution mechanism.

**Acceptance Criteria**:
- Function symbols are resolved using `ns-resolve`
- Undefined function symbols result in clear error messages
- Private functions can be used as handlers
- Functions can have any arity but should accept a request map

### FR-006: Custom Metadata Keys
The system MUST allow arbitrary custom metadata keys for application-specific configuration.

**Acceptance Criteria**:
- Any Clojure keyword can be used as a metadata key
- Custom keys are merged into the request map
- No validation is performed on custom metadata values
- Namespaced keywords are supported

Example:
```clojure
(ns my-app.routes.product
  {:endpoint/http {:get 'GET-product}
   :product/category "electronics"
   :cache/ttl 3600})
```

## Non-Functional Requirements

### Performance
- Namespace metadata resolution should be cached
- Repeated requests to the same route should not reload metadata
- Function symbol resolution happens once per route

### Usability
- Error messages should clearly indicate missing or invalid metadata
- Common mistakes (unquoted symbols, wrong keywords) should be detected
- Documentation examples should follow best practices

### Maintainability
- Metadata structure should be extensible for future features
- Reserved `:endpoint/*` namespace should be documented
- Breaking changes to metadata format should be avoided

## Edge Cases

1. **Missing HTTP Method**: If no handler exists for the request method, return 405 Method Not Allowed
2. **Circular Template References**: Detect and prevent infinite loops in `:endpoint/type` delegation
3. **Invalid Function Symbols**: Provide clear error when symbol doesn't resolve to a function
4. **Nil Metadata**: Handle namespaces with no metadata gracefully
5. **Conflicting Keys**: System keys (`:endpoint/*`) override custom metadata with same names

## Template Delegation Flow

When a namespace has `:endpoint/type` metadata:

1. Original URI matches namespace A
2. Namespace A has no matching `:endpoint/http` method
3. System reads `:endpoint/type` from namespace A → points to namespace B
4. System looks up `:endpoint/http` in namespace B
5. Handler in namespace B is invoked with:
   - `:endpoint/ns` = namespace A (original match)
   - All metadata from namespace A merged into request
6. Handler can reference back to namespace A using `:endpoint/ns`

This enables namespace B to act as a template that can introspect and delegate to namespace A.

## Examples

### Basic HTTP Method Handlers
```clojure
(ns my-app.routes.things
  {:endpoint/http {:get 'GET-things
                   :post 'POST-create-thing}})

(defn GET-things [request]
  {:status 200
   :body "<h1>All Things</h1>"})

(defn POST-create-thing [request]
  {:status 201
   :body "<h1>Thing Created</h1>"})
```

### Template with Custom Metadata
```clojure
;; Route namespace
(ns my-app.routes.blog.2025-01-15_My_Post
  {:endpoint/type 'my-app.templates.blog-post
   :blog/author "Alice"
   :blog/tags ["clojure" "web"]})

(defn content []
  "<p>Blog post content here</p>")

;; Template namespace
(ns my-app.templates.blog-post
  {:endpoint/http {:get 'GET-blog-post}})

(defn GET-blog-post [{:keys [endpoint/ns blog/author blog/tags] :as request}]
  (let [content-fn (ns-resolve ns 'content)]
    (str "<h1>Blog Post</h1>"
         "<p>By: " author "</p>"
         "<p>Tags: " (str/join ", " tags) "</p>"
         (content-fn))))
```

## Dependencies

- Requires URI to File Routing for namespace matching
- Integrates with Ring Middleware for request/response handling
- Uses Clojure's namespace and metadata mechanisms

## Related Requirements

- [URI to File Routing](uri-to-file-routing.md) - Describes how namespaces are matched from URIs
- [Ring Middleware Integration](ring-middleware.md) - Describes how handlers are invoked
