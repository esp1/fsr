# fsr
A filesystem router for Clojure web projects.

**fsr** lets you configure web server routes for dynamic content simply by [arranging](#uri-to-file-route-matching) [annotated](#namespace-annotations) Clojure files in your filesystem. 

It also allows you to [statically publish](#) content from all of your HTTP GET endpoints, so you can use it as a static site generator.

# Setup
1. Add the fsr dependency to your `deps.edn` file:
```
io.github.esp1/fsr {:git/sha "ed5af74ae8b4a08d7c2a37879dcee6a7328935d2"}
```

2. Wrap your Ring application handler with the `wrap-fs-router` [middleware](https://github.com/ring-clojure/ring/wiki/Concepts#handlers) and configure it with a **root filesystem prefix**. This is a path within your Clojure source directory where fsr will resolve routes. In the exmaple configuration below the root filesystem path is `src/my_app/pages`:
```
(ns my-app.server
  (:require [esp1.fsr.ring :refer [wrap-fs-router]]))

(def app
  (-> handler
      (wrap-fs-router "src/my_app/pages")))
```

3. Add routes by placing Clojure files with handler functions under `src/my_app/pages`. See below for details:

# URI to File Route Matching
The following examples assume your fsr root filesystem prefix is `src/my_app/pages`.

## Simple Route Matching
| URI | Clojure file | Clojure namespace |
| --- | ------------ | ----------------- |
| **/foo** | src/my_app/pages/**foo.clj** | my-app.pages.**foo** |
| **/foo** | src/my_app/pages/**foo/index.clj** | my-app.pages.**index** |
| **/foo-bar** | src/my_app/pages/**foo_bar.clj** | my-app.pages.**foo-bar** |

fsr matches URI routes by looking under the root filesystem prefix for a `.clj` file with the same file path as the URI.

Alternatively, it will look under the root filesystem prefix for an `index.clj` file in a directory with the same directory path as the URI.

URIs are matched against namespace names, not filenames. Dashes (`-`) in Clojure namespace names are converted to underscores (`_`) in their filenames. So you can match URIs with dashes in them by using dashes in your namespace names - just remember that their filenames will have those dashes converted to underscores.

## Path Parameters
| URI | Clojure file | Clojure namespace | Path Parameters |
| --- | ------------ | ----------------- | --------------- |
| **/thing/123** | src/my_app/pages/**thing/\<id\>.clj** | my-app.pages.**thing.\<id\>** | {:id "123"} |
| **/category/humor/title/Sorry/NotSorry** | src/my_app/pages/**category/\<category\>/title/\<\<title\>\>.clj** | my-app.pages.**category.\<category\>.title.\<\<title\>\>** | {:category "humor", :title "Sorry/NotSorry"} |

fsr supports routes with dynamic path parameters. Path parameters are indicated by surrounding the parameter name with single angle brackets (`<` `>`) for parameter values not containing slashes (`/`), or double angle brackets (`<<` `>>`) for parameter values that may contain any value, including slashes. Note that if you use a double angle bracket parameter, it must occur at the end of the URI path.

When a route with path parameters is matched, the route handler function's `request` object will contain a `:endpoint/path-params` key whose value is a map of the keywordized parameter names to their values pulled from the URI. Path parameter values are always strings.

# Namespace Annotations
Once fsr matches a URI to a Clojure file, it needs to find the function within that file to handle the request. To do this, fsr looks in the namespace metadata for a `:endpoint/http` key, whose value is a map of http method keywords to function symbols.

```
(ns my-app.pages.thing
  {:endpoint/http {:get 'GET
                   :post 'POST}})

(defn GET [request]
  ;; Response: List all the things
  )

(defn POST [request]
  ;; Create a new thing, assign it an ID
  ;; Response: Redirect to /thing/id
  )
```

In the example above, the namespace metadata tells fsr to use the `GET` function to service HTTP GET requests, and to use the `POST` method to service HTTP POST requests.

Here is another example that also demonstrates dynamic path parameters:
```
(ns com.example.pages.thing.<id>
  {:endpoint/http {:get 'GET
                   :delete 'DELETE}})

(defn GET
  [{:as request
    {:keys [id]} :endpoint/path-params}]
  ;; Response: Show the thing with ID 'id'
  )

(defn DELETE
  [{:as request
    {:keys [id]} :endpoint/path-params}]
  ;; Delete the thing with ID 'id'
  ;; Response: Redirect to /thing
  )
```

# Static Site Generation
You can also use fsr to generate a static site, if all your endpoint functions use HTTP GET methods (static sites only support GET methods).
You can do this by calling `esp1.fsr.static/publish-static` and providing it with your root filesystem prefix, and a directory to publish the static content to.

```
(require '[esp1.fsr.static :refer [publish-static]])

(publish-static "src/my_app/pages" "dist")
```

`publish-static` searches for all fsr HTTP GET endpoint functions under the root filesystem prefix, and invokes them with the URI corresponding to their location in the filesystem, as long as that URI does not contain any path parameters.

## Tracking URIs with path parameters
If you have endpoint functions that use dynamic path parameters, you can still generate them statically, but you will need to have your code track their URIs.
To do this, all you need to do is wrap any code that generates a dynamic URI with the `esp1.fsr.track-uri/track-uri` function.

Here is an example where we wrap the code that constructs the `:href` value with `track-uri`:
```
[:a {:href (track-uri (str "/thing/" thing-id))}
  thing-id]
```

Once you track your dynamic URIs in this manner, `publish-static` will be able to generate static content for those URIs as well.