# Static Site Generation

## Purpose

Generate deployable static websites from fsr routes.

## Value

Build dynamic sites in dev, deploy static HTML to CDN/S3/GitHub Pages without Clojure runtime.

## Requirements

**FR-001: Entry Point** - `publish-static` generates complete static site:
```clojure
(publish-static "src/my_app/routes" "dist")
```

**FR-002: GET Discovery** - Auto-discover all namespaces with `:get` in `:endpoint/http` (including via `:endpoint/type` delegation).

**FR-003: Static Route Generation** - Routes without path parameters auto-generate (`/about.clj` → `dist/about.html`).

**FR-004: URI Tracking** - `track-uri` registers parameterized URIs for generation:
```clojure
[:a {:href (track-uri (str "/blog/" post-id))} "Read"]
```

**FR-005: Tracked URI Generation** - Tracked URIs matched against route patterns, parameters extracted, handlers invoked.

**FR-006: File Organization** - Output matches URI structure:
- `/about` → `dist/about.html` or `dist/about/index.html`
- `/products/widget` → `dist/products/widget.html`

**FR-007: Response Handling**
- String → write to HTML file
- Ring map with `:body` → write body to file
- `nil` → skip or create empty file

**FR-008: Content Type** - Use file extension from `Content-Type` header (default `.html`).

**FR-009: Synthetic Requests** - Handlers invoked with `:uri`, `:request-method :get`, `:endpoint/ns`, `:endpoint/path-params`, and namespace metadata.

**FR-010: Error Handling** - Errors reported with URI context; failed routes don't block others.

**FR-011: Root Index** - Empty URIs (from root `index.clj`) normalized to `"index"` for resolution.

**FR-012: Tracked URI Normalization** - Leading slashes stripped from tracked URIs before resolution.

## Edge Cases

- Circular URI tracking → prevent infinite loops
- Missing output directory → create automatically
- Handler depends on session/cookies → may fail in static generation
- Duplicate URIs → first match wins

## Related

- [uri-to-file-routing.md](uri-to-file-routing.md) - Route resolution
- [namespace-metadata.md](namespace-metadata.md) - Handler configuration
- [compiled-route-production.md](compiled-route-production.md) - Non-GET route compilation
