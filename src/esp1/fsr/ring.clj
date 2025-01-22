(ns esp1.fsr.ring
  (:require [clojure.java.io :as io]
            [esp1.fsr.core :refer [clj->ns-sym
                                   file->clj
                                   get-root-ns-prefix
                                   http-endpoint-fn
                                   ns-endpoint-meta
                                   ns-sym->uri
                                   uri->file+params]]))

(defn uri->endpoint-fn
  "Returns a Ring handler function for the given HTTP method and URI endpoint
   by searching for a Clojure namespace under the given root filesystem path.

   The namespace should have a metadata map with a `:endpoint/http` key
   or a `:endpoint/type` key.
   
   The `:endpoint/http` value should be a map of lowercase keywordized HTTP method names (e.g. `:get`, `:post`)
   to handler function symbols, e.g.:
   ```
   (ns my-app.routes.index
     {:endpoint/http {:get 'show-create-new-thing-page-endpoint
                      :post 'create-new-thing-endpoint}})
   ```
   
   If no matching function symbol is found in the `:endpoint/http` map for the given HTTP method,
   will look for a `:endpoint/type` key whose value is a symbol of another namespace
   in which to recursively look for a `:endpoint/http` or `:endpoint/type` key
   and a matching endpoint function.
   
   If no appropriate handler function can be found in the filesystem, returns nil.

   If an endpoint handler function is found, it will be augmented so that
   the Ring request map passed as an argument to the function when it is invoked
   will have the metadata map of the namespace matching the URI merged into it,
   and will also contain the following keys:
   - `:endpoint/uri` - The URI associated with this namespace. If a `ns-prefix` is specified, it is elided from the URI path.
   - `:endpoint/ns` - The symbol for the namespace matching the URI.

   If the handler namespace name contains path parameters,
   the request map will contain an additional `:endpoint/path-params` key
   whose value maps the string parameter names to their values in the request URI.

   Path parameters in URIs are designated by surrounding the parameter name
   with single angle brackets `<` `>` to match a parameter value that does not contain a slash `/` character,
   or double angle brackets `<<` `>>` to match a parameter value that may contain a slash `/` character."
  [method uri root-fs-path]
  (when-let [[f path-params] (uri->file+params uri (io/file root-fs-path))]
    (let [ns-sym (-> f
                     file->clj
                     clj->ns-sym)
          endpoint-meta (-> (ns-endpoint-meta ns-sym)
                            (assoc :endpoint/uri (ns-sym->uri ns-sym (get-root-ns-prefix root-fs-path))))]
      (when-let [http-fn (http-endpoint-fn method endpoint-meta)]
        (fn [request]
          (http-fn (merge request
                          (-> endpoint-meta
                              (assoc :endpoint/path-params path-params)))))))))

(defn wrap-fs-router
  "Ring middleware that wraps the provided handler
   and adds the ability to respond to requests with handler functions found under the given root filesystem path via `uri->endpoint-fn`.
   The handler function is expected to return either:
   - a Ring response map
   - a string, which fsr will use as the body of an HTTP 200 Ok response
   - nil, which fsr will translate into an HTTP 204 No Content response
   "
  [handler root-fs-path]
  (fn [{:as request
        :keys [request-method uri]}]
    (if-let [endpoint-fn (uri->endpoint-fn request-method uri root-fs-path)]
      ;; use endpoint-fn to generate content
      (let [response (endpoint-fn request)]
        (cond
          ;; if response is a string, treat it as an HTML response
          (string? response)
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body response}

          ;; if response is a map, use it as the response map
          (map? response)
          response

          ;; if response is nil, send HTTP 204 No Content response
          (nil? response)
          {:status 204}

          :else
          (throw (Exception. "Invalid response type. Endpoint function should return a string or a response map."))))

      ;; else Not Found
      (handler request))))