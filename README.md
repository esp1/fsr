# fsr
A filesystem router for Clojure web projects.

**fsr** lets you configure web server routes for dynamic content simply by [arranging](#uri-to-file-route-matching) [annotated](#namespace-annotations) Clojure namespace files in your source directory in a way that closely mirrors the structure of your web site. At its core is functionality to map between URIs and filesystem paths, including URIs with dynamic [path parameters](#path-parameters). Its namespace metadata and route resolution mechanisms can be leveraged to implement [custom templates](#custom-templates) for your content. And it can also be used as a [static site generator](#static-site-generation).

[API docs](https://esp1.github.io/fsr/api/)

# Usage
1. Add the fsr dependency to your `deps.edn` file:  
```clojure
io.github.esp1/fsr {:git/tag "v1.0.0", :git/sha "7cad5cb"}
```

2. Wrap your Ring application handler with the `wrap-fs-router` [middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware) and configure it with a **root filesystem path**. This is a path within your Clojure source directory where fsr will resolve routes. In the exmaple configuration below the root filesystem path is `src/my_app/routes`:  
```clojure
(ns my-app.server
  (:require [esp1.fsr.ring :refer [wrap-fs-router]]))

(def app
  (-> handler
      (wrap-fs-router "src/my_app/routes")))
```

3. Now just place `.clj` source files under your fsr root filesystem path, and add a bit of namespace metadata to point to your handler functions. For example, here is a `src/my_app/routes/index.clj` file that renders the homepage for the root `/` URL:  
```clojure
(ns my-app.routes.index
  {:endpoint/http {:get 'GET-homepage}})

(defn GET-homepage [request]
  "<h1>Welcome to my humble home</h1>")
```

See below for more usage details.

# URI to File Route Matching
The following examples assume your fsr root filesystem path is `src/my_app/routes`. For your own usage, replace this with whatever you have configured your fsr root filesystem path to be.

## Simple Route Matching
| URI | Clojure file | Clojure namespace |
| --- | ------------ | ----------------- |
| **/foo** | src/my_app/routes/**foo.clj** | my-app.routes.**foo** |
| **/foo** | src/my_app/routes/**foo/index.clj** | my-app.routes.**index** |
| **/foo-bar** | src/my_app/routes/**foo_bar.clj** | my-app.routes.**foo-bar** |

fsr matches URI routes by looking under the fsr root filesystem path for a `.clj` file with the same file path as the URI.

Alternatively, it will look under the fsr root filesystem path for an `index.clj` file in a directory with the same directory path as the URI.

URIs are matched against namespace names, not filenames. Dashes (`-`) in Clojure namespace names are converted to underscores (`_`) in their filenames. So you can match URIs with dashes in them by using dashes in your namespace names - just remember that their filenames will have those dashes converted to underscores.

## Path Parameters
| URI | Clojure file | Clojure namespace | Path Parameters |
| --- | ------------ | ----------------- | --------------- |
| **/thing/123** | src/my_app/routes/**thing/\<id\>.clj** | my-app.routes.**thing.\<id\>** | {:id "123"} |
| **/category/humor/title/Sorry/NotSorry** | src/my_app/routes/**category/\<category\>/title/\<\<title\>\>.clj** | my-app.routes.**category.\<category\>.title.\<\<title\>\>** | {:category "humor", :title "Sorry/NotSorry"} |

fsr supports routes with dynamic path parameters. Path parameters are indicated by surrounding the parameter name with single angle brackets (`<` `>`) for parameter values not containing slashes (`/`), or double angle brackets (`<<` `>>`) for parameter values that may contain any value, including slashes. Note that if you use a double angle bracket parameter, it should occur at the end of the URI path.

When a route with path parameters is matched, the route handler function's `request` object will contain a `:endpoint/path-params` key whose value is a map of the string parameter names to their values pulled from the URI. Path parameter values are always strings.

# Namespace Annotations
Once fsr matches a URI to a Clojure file, it needs to find the function within that file to handle the request. To do this, fsr looks in the namespace metadata for a `:endpoint/http` key, whose value is a map of http method keywords to function symbols.

```clojure
(ns my-app.routes.thing
  {:endpoint/http {:get 'GET-create-thing
                   :post 'POST-create-thing}})

(defn GET-create-thing
  [request]
  ;; Response: Show page with form to create new thing
  )

(defn POST-create-thing
  [request]
  ;; Create a new thing, assign it an ID
  ;; Response: Redirect to /thing/id
  )
```

In the example above, the namespace metadata tells fsr to use the `GET-create-thing` function to service HTTP GET requests, and to use the `POST-create-thing` method to service HTTP POST requests for the `/thing` URI.

If no matching handler function is found in the URI namespace metadata's `:endpoint/http` map, fsr will look for a `:endpoint/type` key in the namespace metadata, whose value is a symbol of another namespace in which to recursively look for a `:endpoint/http` or `:endpoint/type` key and a matching endpoint function.

## Custom Templates
The above `:endpoint/type` functionality can be used to implement custom templating mechanisms.

Here is an example of a blog page that uses a template defined in `my-app.layouts.blog-post`. It defines a `content` function that just renders the blog content itself, without the surrounding page header and footer.
```clojure
(ns my-app.routes.blog.2025-01-01
  {:endpoint/type 'my-app.layouts.blog-post})

(defn content []
  ;; Render blog contents
  )
```

HTTP GET requests to this page will be serviced by the `:get` endpoint function in the `my-app.layouts.blog-post` namespace:
```clojure
(ns my-app.layouts.blog-post
  {:endpoint/http {:get 'GET-blog-post}})

(defn GET-blog-post
  [{:as request
    ns-sym :endpoint/ns}]
  ;; Render blog page template header

  ;; Call content function to render blog content
  (let [content-fn (ns-resolve ns-sym 'content)]
    (content-fn))
  
  ;; Render blog page template footer
  )
```

The `GET-blog-post` function renders the page header and footer, and in between resolves the `content` function of the namespace matching the URI (via the `:endpoint/ns` attribute which fsr automatically adds to the request map - see [Handler Functions](#handler-functions) below) and calls it.

This is a very simple example, but it can easily be extended to support passing additional parameters from the template to the content function, passing additional information to the template function by adding things to the URI namespace metadata, etc.

# Handler Functions
Hander functions are called with a [Ring request map](https://github.com/ring-clojure/ring/wiki/Concepts#requests) that will have the metadata map of the namespace matching the URI merged into it, and also contain a `:endpoint/ns` key whose value is the symbol for the namespace matching the URI.

If the handler namespace name contains path parameters, the request map will contain an additional `:endpoint/path-params` key whose value maps the string parameter names to their values in the request URI.

```clojure
(ns my-app.routes.thing.<id>
  {:endpoint/http {:get 'GET-thing
                   :put 'PUT-thing
                   :delete 'DELETE-thing}})

(defn GET-thing
  [{:as request
    {:strs [id]} :endpoint/path-params}]
  ;; Response: Show the thing with ID 'id'
  )

(defn PUT-thing
  [{:as request
    {:strs [id]} :endpoint/path-params}]
  ;; Update the thing with ID 'id'
  ;; Response: Show the updated thing with ID 'id'
  )

(defn DELETE-thing
  [{:as request
    {:strs [id]} :endpoint/path-params}]
  ;; Delete the thing with ID 'id'
  ;; Response: Redirect to /thing
  )
```

## Handler Responses
Handler functions are expected to return either:
- a **string**, which fsr will treat as the `:body` of an `HTTP 200 Ok` Ring response  
```clojure
(defn string-handler
  [request]
  "<h1>stuff</h1>")
```
- a **[Ring response map](https://github.com/ring-clojure/ring/wiki/Concepts#responses)**  
```clojure
(defn ring-response-handler
  [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<h1>stuff</h1>"})
```
-  or **`nil`**, which fsr will translate into a `HTTP 204 No Content` Ring response

# Static Site Generation
You can also use fsr to generate a static site, if all your endpoint functions use HTTP GET methods (static sites only support GET methods).
You can do this by calling `esp1.fsr.static/publish-static` and providing it with your fsr root filesystem path, and a directory to publish the static content to.

```clojure
(require '[esp1.fsr.static :refer [publish-static]])

(publish-static "src/my_app/routes" "dist")
```

`publish-static` searches for all fsr HTTP GET endpoint functions under the fsr root filesystem path, and invokes them with the URI corresponding to their location in the filesystem, as long as that URI does not contain any path parameters.

## Tracking URIs with path parameters
If you have endpoint functions that use dynamic path parameters, you can still generate them statically, but you will need to have your code track their URIs.
To do this, all you need to do is wrap any code that generates a dynamic URI with the `esp1.fsr.static/track-uri` function.

Here is an example where we wrap the code that constructs the `:href` value with `track-uri`:
```clojure
[:a {:href (track-uri (str "/thing/" thing-id))}
  thing-id]
```

Once you track your dynamic URIs in this manner, `publish-static` will be able to generate static content for those URIs as well.
