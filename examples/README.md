# fsr Production Deployment Examples

This directory contains example scripts for deploying fsr applications to production using compiled routes.

## Overview

Production deployment with fsr involves two phases:

1. **Build Phase**: Compile routes and generate static files
2. **Runtime Phase**: Serve static files + handle dynamic routes with compiled route matching

## Files

- `build.clj` - Build script for compiling routes and generating static site
- `production_server.clj` - Production server using compiled routes
- `sample_routes/` - Example route structure for testing

## Build Phase

The build script generates:
- **Static HTML files** for GET routes (in `dist/`)
- **Compiled routes EDN** for non-GET routes (in `dist/compiled-routes.edn`)

### Using the Build Script

```bash
# Build with defaults (routes from src/routes, output to dist/)
bb examples/build.clj

# Custom output directory
bb examples/build.clj --output ./public

# Custom routes directory
bb examples/build.clj --routes src/my_app/routes --output deploy

# See help
bb examples/build.clj --help
```

### Build Output

```
dist/
├── index.html                    # Static HTML for GET /
├── about/
│   └── index.html                # Static HTML for GET /about
├── blog/
│   ├── post-1/index.html         # Static HTML for GET /blog/post-1
│   └── post-2/index.html         # Static HTML for GET /blog/post-2
└── compiled-routes.edn           # Compiled non-GET routes
```

### Programmatic Build

```clojure
(require '[esp1.fsr.compile :refer [publish]])

(publish {:root-fs-path "src/routes"
          :publish-dir "dist"
          :compile-routes? true})

;; Returns:
;; {:static-files-generated :unknown
;;  :compiled-routes-file "dist/compiled-routes.edn"
;;  :static-routes-count 3
;;  :pattern-routes-count 2}
```

## Runtime Phase

### Production Server Setup

```bash
# Start production server (default port 3000)
clojure -M -m production-server

# Custom port
PORT=8080 clojure -M -m production-server

# Custom routes file
COMPILED_ROUTES=./public/compiled-routes.edn clojure -M -m production-server
```

### Deployment Architecture

```
┌─────────────────────────────────────────────────┐
│                   Load Balancer                  │
└──────────────┬──────────────────────────────────┘
               │
       ┌───────┴────────┐
       │                │
       ▼                ▼
┌────────────┐   ┌────────────┐
│ Static CDN │   │ App Server │
│            │   │            │
│ GET /      │   │ POST /api/ │
│ GET /about │   │ PUT /api/  │
│ GET /blog  │   │ DELETE ... │
└────────────┘   └────────────┘
       │                │
       │         ┌──────┴──────────────┐
       │         │ compiled-routes.edn │
       │         │ handler namespaces  │
       │         └─────────────────────┘
       │
    Static        Runtime route matching
    HTML files    (no filesystem access)
```

### Deployment Steps

1. **Build**: Run build script to generate `dist/`
2. **Static Files**: Deploy `dist/*.html` to CDN/S3/Netlify
3. **Application Server**:
   - Deploy `dist/compiled-routes.edn`
   - Deploy compiled handler `.class` files (or uberjar)
   - Start server with `production-server.clj`
4. **Configure Load Balancer**:
   - Route GET requests to static CDN
   - Route POST/PUT/DELETE to app server

## Example Route Structure

```
src/routes/
├── index.clj                      # GET / → static HTML
├── about.clj                      # GET /about → static HTML
├── api/
│   ├── users.clj                  # POST /api/users → compiled route
│   └── posts.clj                  # POST /api/posts → compiled route
└── blog/
    └── <slug>.clj                 # GET /blog/:slug (tracked) → static HTML
                                   # PUT /blog/:slug → compiled route
```

### Example Route File

```clojure
;; src/routes/api/users.clj
(ns routes.api.users
  {:endpoint/http {:post 'create-user
                   :get 'list-users
                   :delete 'delete-all}})

(defn create-user
  "POST /api/users - Creates a new user"
  [{:keys [body-params endpoint/ns endpoint/path-params]}]
  {:status 201
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string {:id (random-uuid)
                                :name (:name body-params)})})

(defn list-users
  "GET /api/users - Lists all users (static generated)"
  [request]
  {:status 200
   :body "<html>...</html>"})

(defn delete-all
  "DELETE /api/users - Deletes all users"
  [request]
  {:status 204})
```

## Performance Comparison

| Deployment Mode | Startup Time | First Request | Memory |
|----------------|--------------|---------------|--------|
| Dev (filesystem) | ~500ms | ~50ms (cold cache) | ~100MB |
| Production (compiled) | ~200ms | ~1ms (static), ~5ms (pattern) | ~50MB |

## Security Considerations

Production deployments with compiled routes:
- ✅ No source `.clj` files needed
- ✅ No filesystem access at runtime
- ✅ Reduced attack surface
- ✅ Smaller deployment artifact
- ✅ Predictable resource usage

## Troubleshooting

### Build Fails

```bash
# Check that routes directory exists
ls -la src/routes

# Check for syntax errors in route files
clojure -M:dev -e "(require 'routes.api.users)"
```

### Server Fails to Start

```bash
# Verify compiled-routes.edn exists
cat dist/compiled-routes.edn

# Check EDN is valid
clojure -M:dev -e "(clojure.edn/read-string (slurp \"dist/compiled-routes.edn\"))"
```

### Routes Not Matching

```bash
# Check compiled routes structure
clojure -M:dev -e "
  (require '[esp1.fsr.runtime :as rt])
  (def routes (rt/load-compiled-routes \"dist/compiled-routes.edn\"))
  (rt/match-route \"/api/users\" :post routes)
"
```

## Further Reading

- [Production Route Compilation Spec](../docs/spec/compiled-route-production.md)
- [Route Compilation Technical Details](../docs/tech/route-compilation.md)
- [Static Site Generation](../docs/spec/static-site-generation.md)
