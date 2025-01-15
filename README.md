# fsr
A filesystem router for Clojure web projects.

**fsr** lets you configure web server routes for dynamic content simply by [arranging](#uri-to-file-route-matching) [annotated](#namespace-annotations) Clojure namespace files in your source directory in a way that closely mirrors the structure of your web site. It supports dynamic [path parameters](#path-parameters) in route URIs, and can also be used as a [static site generator](#static-site-generation).

# Setup
1. Add the fsr dependency to your `deps.edn` file:
```
io.github.esp1/fsr {:git/sha "29849c506e5bf817df9fca36897e5ea9bf7a3e5a"}
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

When a route with path parameters is matched, the route handler function's `request` object will contain a `:endpoint/path-params` key whose value is a map of the string parameter names to their values pulled from the URI. Path parameter values are always strings.

# Namespace Annotations
Once fsr matches a URI to a Clojure file, it needs to find the function within that file to handle the request. To do this, fsr looks in the namespace metadata for a `:endpoint/http` key, whose value is a map of http method keywords to function symbols.

```
(ns my-app.pages.thing
  {:endpoint/http {:get 'show-create-new-thing-page-endpoint
                   :post 'create-new-thing-endpoint}})

(defn show-create-new-thing-page-endpoint
  [request]
  ;; Response: Show page with form to create new thing
  )

(defn create-new-thing-endpoint
  [request]
  ;; Create a new thing, assign it an ID
  ;; Response: Redirect to /thing/id
  )
```

In the example above, the namespace metadata tells fsr to use the `show-create-new-thing-page-endpoint` function to service HTTP GET requests, and to use the `create-new-thing-endpoint` method to service HTTP POST requests for the `/thing` URI.

# Handler Functions
Hander functions are called with a [Ring request map](https://github.com/ring-clojure/ring/wiki/Concepts#requests) object. If the handler namespace contains path parameters, the Ring request will contain an additional `:endpoint/path-params` key whose value maps the string parameter names to their values in the request URI.

```
(ns com.example.pages.thing.<id>
  {:endpoint/http {:get 'show-thing-page-endpoint
                   :put 'update-thing-endpoint
                   :delete 'delete-thing-endpoint}})

(defn show-thing-page-endpoint
  [{:as request
    {:strs [id]} :endpoint/path-params}]
  ;; Response: Show the thing with ID 'id'
  )

(defn update-thing-page-endpoint
  [{:as request
    {:strs [id]} :endpoint/path-params}]
  ;; Update the thing with ID 'id'
  ;; Response: Show the updated thing with ID 'id'
  )

(defn delete-thing-endpoint
  [{:as request
    {:strs [id]} :endpoint/path-params}]
  ;; Delete the thing with ID 'id'
  ;; Response: Redirect to /thing
  )
```

## Handler Responses
Handler functions are expected to return a [Ring response map](https://github.com/ring-clojure/ring/wiki/Concepts#responses).

For convenience fsr also allows handler functions to return a string response, in which case fsr will use that string as the `:body` value in a `HTTP 200 Ok` Ring response:
```
{:status 200
 :headers {"Content-Type" "text/html"}
 :body response}
```

Also for convenience if a handler function returns `nil`, fsr will translate that into a `HTTP 204 No Content` Ring response:
```
{:status 204}
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
