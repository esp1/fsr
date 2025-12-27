# fsr

A filesystem router for Clojure web projects.

**fsr** is a zero-dependency Clojure library that maps HTTP request URIs to Clojure namespace files based on filesystem structure. It supports dynamic path parameters, custom templates via namespace metadata, and can function as a static site generator.

## Quick Start

1. Add the dependency to your `deps.edn`:
```clojure
io.github.esp1/fsr {:git/tag "v1.0.0", :git/sha "7cad5cb"}
```

2. Wrap your Ring handler with `wrap-fs-router`:
```clojure
(ns my-app.server
  (:require [esp1.fsr.ring :refer [wrap-fs-router]]))

(def app
  (-> handler
      (wrap-fs-router "src/my_app/routes")))
```

3. Create route files with namespace metadata:
```clojure
;; src/my_app/routes/index.clj
(ns my-app.routes.index
  {:endpoint/http {:get 'GET-homepage}})

(defn GET-homepage [request]
  "<h1>Welcome to my humble home</h1>")
```

## Core Features

- **Filesystem-based routing**: URIs map to `.clj` files (`/about` → `about.clj` or `about/index.clj`)
- **Path parameters**: Use `<param>` for segments without slashes, `<<param>>` for segments with slashes
- **Namespace metadata**: Configure handlers via `:endpoint/http` and `:endpoint/type` metadata
- **Custom templates**: Delegate rendering to template namespaces
- **Static site generation**: Generate deployable static sites from your routes
- **Hot-reload support**: Automatic code reloading in development mode
- **Zero dependencies**: Core library has no external dependencies

## Example: Path Parameters

```clojure
;; File: src/my_app/routes/user/<id>.clj
(ns my-app.routes.user.<id>
  {:endpoint/http {:get 'GET-user}})

(defn GET-user [{:keys [endpoint/path-params]}]
  (let [id (get path-params "id")]
    (str "<h1>User " id "</h1>")))
```

Request to `/user/123` → `path-params` = `{"id" "123"}`

## Documentation

- **[Functional Requirements](functional/index.md)** - Feature overview and use cases
- **[Technical Documentation](technical/index.md)** - Architecture, implementation, development
