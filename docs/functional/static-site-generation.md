# Static Site Generation

## Purpose

Generate deployable static websites from fsr routes.

## Value

Build dynamic sites in dev, deploy static HTML to CDN/S3/GitHub Pages without Clojure runtime.

## Requirements

**Entry Point** - `publish-static` generates complete static site:
```clojure
(publish-static "src/my_app/routes" "dist")
```

**GET Discovery** - Auto-discover all namespaces with `:get` in `:endpoint/http` (including via `:endpoint/type` delegation).

**Static Route Generation** - Routes without path parameters auto-generate (`/about.clj` → `dist/about.html`).

**URI Tracking** - `track-uri` registers parameterized URIs for generation:
```clojure
[:a {:href (track-uri (str "/blog/" post-id))} "Read"]
```

**Tracked URI Generation** - Tracked URIs matched against route patterns, parameters extracted, handlers invoked.

**File Organization** - Output matches URI structure:
- `/about` → `dist/about.html` or `dist/about/index.html`
- `/products/widget` → `dist/products/widget.html`

**Response Handling**
- String → write to HTML file
- Ring map with `:body` → write body to file
- `nil` → skip or create empty file

**Content Type** - Use file extension from `Content-Type` header (default `.html`).

**Synthetic Requests** - Handlers invoked with `:uri`, `:request-method :get`, `:endpoint/ns`, `:endpoint/path-params`, and namespace metadata.

**Error Handling** - Errors reported with URI context; failed routes don't block others.

**Root Index** - Empty URIs (from root `index.clj`) normalized to `"index"` for resolution.

**Tracked URI Normalization** - Leading slashes stripped from tracked URIs before resolution.

## Edge Cases

- Circular URI tracking → prevent infinite loops
- Missing output directory → create automatically
- Handler depends on session/cookies → may fail in static generation
- Duplicate URIs → first match wins

## Related

- **[URI to File Routing](uri-to-file-routing.md)** - Route resolution
- **[Namespace Metadata](namespace-metadata.md)** - Handler configuration
- **[Production Compilation](compiled-route-production.md)** - Non-GET route compilation
