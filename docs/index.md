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

## Documentation

- **[Functional Requirements](functional/index.md)** - Feature overview and use cases
- **[Technical Documentation](technical/index.md)** - Architecture, implementation, development
