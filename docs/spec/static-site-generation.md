# Feature Specification: Static Site Generation

## Overview

The Static Site Generation feature enables developers to convert their fsr-based dynamic web applications into deployable static websites. This allows the same codebase to be used for both dynamic development and static production deployment.

## User Value

Developers can build and preview their sites as dynamic applications during development, then generate a complete static site for deployment to simple hosting services (CDN, S3, GitHub Pages) without requiring a Clojure runtime in production.

## User Stories

### Primary Story
**As a** web developer
**I want** to generate a complete static website from my fsr routes
**So that** I can deploy to static hosting services without needing a server

### Supporting Stories

1. **Automatic Route Discovery**
   - **As a** developer
   - **I want** the system to automatically find all my GET routes
   - **So that** I don't have to manually list every page to generate

2. **Dynamic URI Tracking**
   - **As a** developer
   - **I want** to track specific URIs with path parameters during development
   - **So that** those dynamic pages are included in static generation

3. **Organized Output**
   - **As a** developer
   - **I want** generated files organized in a directory structure matching my URIs
   - **So that** the static site works with standard web servers

## Functional Requirements

### FR-001: Static Generation Entry Point
The system MUST provide a `publish-static` function that generates a complete static site from fsr routes.

**Acceptance Criteria**:
- Function signature: `(publish-static root-fs-path output-dir)`
- `root-fs-path` is the source directory containing route files
- `output-dir` is the destination directory for generated files
- Function returns successfully when all routes are generated
- Errors during generation are reported with route context

Example:
```clojure
(require '[esp1.fsr.static :refer [publish-static]])
(publish-static "src/my_app/routes" "dist")
```

### FR-002: Automatic GET Endpoint Discovery
The system MUST automatically discover all GET endpoints in the route filesystem.

**Acceptance Criteria**:
- All .clj files under root-fs-path are scanned
- Namespaces with `:endpoint/http` containing `:get` handlers are identified
- Namespaces with `:endpoint/type` delegating to templates with `:get` are included
- Only GET methods are processed (POST, PUT, DELETE are skipped)

### FR-003: Parameter-Free Route Generation
The system MUST automatically generate static files for routes without path parameters.

**Acceptance Criteria**:
- Routes like `/about.clj` are automatically generated
- Route like `/products/index.clj` are automatically generated
- URIs are derived from file paths relative to root-fs-path
- Each route is invoked with a synthetic GET request

### FR-004: URI Tracking for Dynamic Routes
The system MUST provide a `track-uri` function for developers to register URIs with path parameters for static generation.

**Acceptance Criteria**:
- Function signature: `(track-uri uri)`
- `uri` is a complete URI string including parameter values
- Function returns the URI unchanged (can be used inline)
- Tracked URIs are stored for later use by `publish-static`
- Tracking works during normal development request handling

Example:
```clojure
[:a {:href (track-uri (str "/blog/" post-id))} "Read More"]
```

### FR-005: Tracked URI Generation
The system MUST generate static files for all tracked URIs in addition to parameter-free routes.

**Acceptance Criteria**:
- `publish-static` processes all URIs registered via `track-uri`
- Tracked URIs are matched against route patterns with parameters
- Path parameters are extracted and passed to handlers
- Generated files are created at paths corresponding to the full URI

### FR-006: File Output Organization
The system MUST organize generated files in a directory structure matching URI paths.

**Acceptance Criteria**:
- URI `/about` generates `output-dir/about.html` OR `output-dir/about/index.html`
- URI `/products/widget` generates `output-dir/products/widget.html`
- Directory structure is created automatically
- Existing files are overwritten

### FR-007: Response Type Handling
The system MUST handle different handler response types when generating static content.

**Acceptance Criteria**:
- String responses are written directly to HTML files
- Ring response maps with `:body` strings are written to files
- nil responses create empty files or are skipped
- Non-string body content is handled appropriately (byte arrays, etc.)

### FR-008: Content Type Detection
The system MUST use appropriate file extensions based on response content type.

**Acceptance Criteria**:
- Default file extension is `.html`
- If response has `Content-Type` header, use corresponding extension
- `text/html` → `.html`
- `text/css` → `.css`
- `application/json` → `.json`
- Other types use appropriate extensions or default to `.html`

### FR-009: Synthetic Request Construction
The system MUST construct synthetic Ring request maps when invoking handlers for static generation.

**Acceptance Criteria**:
- Request includes `:uri` matching the route being generated
- Request includes `:request-method :get`
- Request includes `:endpoint/ns` and `:endpoint/path-params` as in dynamic mode
- Request includes namespace metadata
- Request is sufficient for handlers to generate complete responses

### FR-010: Generation Error Handling
The system MUST handle errors during generation gracefully and provide clear feedback.

**Acceptance Criteria**:
- Handler exceptions are caught and reported with URI context
- Failed routes do not prevent other routes from generating
- Summary of successful and failed generations is provided
- Error messages include stack traces for debugging

## Non-Functional Requirements

### Performance
- Generation should leverage route caching for resolution
- Large sites (1000+ pages) should complete in reasonable time
- Parallel generation may be considered for performance

### Usability
- Single function call to generate entire site
- Clear progress indication for large sites
- Errors are actionable and include route context

### Compatibility
- Generated sites should work on any static web server
- No server-side processing required
- Standard HTML/CSS/JS conventions

## Edge Cases

1. **Circular URI Tracking**: Prevent infinite loops if handlers track their own URI
2. **Missing Output Directory**: Create output directory if it doesn't exist
3. **Permission Errors**: Clear error if output directory is not writable
4. **Handler Depends on Request Data**: Handlers using session, cookies, etc. may fail in static generation
5. **Duplicate URIs**: If multiple routes could match same URI, use first match

## URI Tracking Strategy

### When to Track
Developers should call `track-uri` any time they generate a dynamic URI:
- In link generation code
- In navigation menus
- In pagination links
- In redirect URLs

### Where to Track
`track-uri` should be called during:
- Development request handling (builds up tracked URI list)
- Dedicated "crawl" phase before static generation
- Seed data initialization

### Example Tracking Pattern
```clojure
(defn render-blog-list [posts]
  (for [post posts]
    [:li
      [:a {:href (track-uri (str "/blog/" (:id post)))}
        (:title post)]]))
```

## Generation Workflow

1. **Initialization**
   - Create output directory if needed
   - Initialize tracked URI collection
   - Clear route cache

2. **Route Discovery**
   - Scan root-fs-path for .clj files
   - Load namespace metadata
   - Identify GET endpoints

3. **URI Collection**
   - Generate URIs for parameter-free routes
   - Include tracked URIs from `track-uri` calls
   - Deduplicate URIs

4. **Content Generation**
   - For each URI:
     - Resolve route and handler
     - Construct synthetic request
     - Invoke handler
     - Capture response
   - Write response to output file
   - Handle errors gracefully

5. **Completion**
   - Report generation summary
   - List any failed routes
   - Return status

## Integration Example

```clojure
(ns my-app.build
  (:require [esp1.fsr.static :refer [publish-static track-uri]]))

;; Route with dynamic parameter
(ns my-app.routes.blog.<id>
  {:endpoint/http {:get 'GET-blog-post}})

(defn GET-blog-post [{:keys [endpoint/path-params]}]
  (let [id (get path-params "id")
        post (db/get-post id)]
    (render-blog-post post)))

;; Route that tracks URIs
(ns my-app.routes.blog.index
  {:endpoint/http {:get 'GET-blog-list}})

(defn GET-blog-list [request]
  (let [posts (db/all-posts)]
    (for [post posts]
      [:a {:href (track-uri (str "/blog/" (:id post)))}])))

;; Build script
(defn -main []
  ;; First, make a request to /blog to build tracking list
  (let [app (wrap-fs-router identity "src/my_app/routes")]
    (app {:uri "/blog" :request-method :get}))

  ;; Now generate static site
  (publish-static "src/my_app/routes" "dist"))
```

## Dependencies

- Requires URI to File Routing for route resolution
- Requires Namespace Metadata System for handler discovery
- Requires Ring Middleware for handler invocation logic
- May use Route Caching for performance

## Related Requirements

- [URI to File Routing](uri-to-file-routing.md) - Describes route resolution
- [Namespace Metadata System](namespace-metadata.md) - Describes handler configuration
- [Ring Middleware Integration](ring-middleware.md) - Describes request/response handling
