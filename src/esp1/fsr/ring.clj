(ns esp1.fsr.ring
  "Provides a `wrap-fs-router` [Ring middleware](https://github.com/ring-clojure/ring/wiki/Concepts#middleware) function that resolves routes
   from Clojure namespaces and functions in the filesystem.

   In development, this middleware provides dynamic routing with automatic hot-reloading.
   In production, use `esp1.fsr.static/publish-static` to generate optimized static assets instead."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [esp1.fsr.cache :as cache]
            [esp1.fsr.core :as core :refer [clj->ns-sym
                                            clear-route-cache!
                                            file->clj
                                            http-endpoint-fn
                                            ns-endpoint-meta
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
   and also contain a `:endpoint/ns` key whose value is the symbol for the namespace matching the URI.

   If the handler namespace name contains path parameters,
   the request map will contain an additional `:endpoint/path-params` key
   whose value maps the string parameter names to their values in the request URI.

   Path parameters in URIs are designated by surrounding the parameter name
   with single angle brackets `<` `>` to match a parameter value that does not contain a slash `/` character,
   or double angle brackets `<<` `>>` to match a parameter value that may contain a slash `/` character."
  {:malli/schema [:=> [:cat :http-method :uri-template :dir-path]
                  [:maybe [:fn #(fn? %)]]]}
  [method uri root-fs-path]
  (when-let [[f path-params] (uri->file+params uri (io/file root-fs-path))]
    (let [endpoint-meta (-> f
                            file->clj
                            clj->ns-sym
                            ns-endpoint-meta)]
      (when-let [http-fn (http-endpoint-fn method endpoint-meta)]
        (fn [request]
          (http-fn (merge request
                          (-> endpoint-meta
                              (assoc :endpoint/path-params path-params)))))))))

(defn wrap-fs-router
  "Ring middleware for development that wraps the provided handler
   and adds the ability to respond to requests with handler functions found under the given root filesystem path.

   This middleware provides dynamic routing with automatic hot-reloading of changed namespaces.
   It clears the route cache on every request and reloads any changed namespaces.

   For production deployments, use `esp1.fsr.static/publish-static` instead to generate
   optimized static assets and/or a minimal server with pre-compiled routes.

   The handler function is expected to return either:
   - a Ring response map
   - a string, which fsr will use as the body of an HTTP 200 Ok response
   - nil, which fsr will translate into an HTTP 204 No Content response

   Options:
   - :hot-reload? - Enable hot-reloading of changed namespaces (default: true, or set via fsr.hot-reload system property)
   - :verbose? - Enable verbose logging of reloaded namespaces"
  [handler root-fs-path & {:keys [hot-reload? verbose?]
                           :or {hot-reload? (not= "false" (System/getProperty "fsr.hot-reload"))
                                verbose? false}}]
  (println "FSR: Dynamic routing enabled")
  (println "     Watching:" root-fs-path)
  (println "     Hot-reload:" (if hot-reload? "enabled" "disabled"))

  ;; Try to set up hot reloading if tools.namespace is available and hot-reload is enabled
  (let [tracker (atom nil)
        can-reload? (and hot-reload?
                         (try
                           (require 'clojure.tools.namespace.track)
                           (require 'clojure.tools.namespace.dir)
                           (require 'clojure.tools.namespace.find)
                           (reset! tracker ((resolve 'clojure.tools.namespace.track/tracker)))
                           true
                           (catch Exception _
                             (println "Note: Install tools.namespace for hot-reload support")
                             false)))]

    (when (and hot-reload? (not can-reload?))
      (println "Warning: Hot-reload requested but tools.namespace not available"))

    (fn [{:as request
          :keys [request-method uri]}]
      ;; Show cache stats before clearing (if verbose)
      (when verbose?
        (let [metrics (cache/get-metrics)]
          (println (format "Cache stats: %d hits, %d misses, hit-rate: %.2f%%"
                           (:hits metrics)
                           (:misses metrics)
                           (* 100.0 (:hit-rate metrics))))))

      ;; Clear route cache on every request for development
      (clear-route-cache!)

      ;; Reload changed namespaces if tools.namespace is available
      (when can-reload?
        (try
          (let [scan-dirs-fn (resolve 'clojure.tools.namespace.dir/scan-dirs)
                new-tracker (swap! tracker scan-dirs-fn [(io/file root-fs-path)])
                changed (get new-tracker :clojure.tools.namespace.track/load [])]
            (when (seq changed)
              (when verbose?
                (println (format "Hot-reload: Reloading %d namespace%s: %s"
                                 (count changed)
                                 (if (= 1 (count changed)) "" "s")
                                 (str/join ", " changed))))
              (doseq [ns-sym changed]
                (try
                  (require ns-sym :reload)
                  (catch Exception e
                    (println (format "Error reloading namespace %s: %s" ns-sym (.getMessage e))))))))
          (catch Exception e
            (println "Error during hot-reload scan:" (.getMessage e)))))

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
        (handler request)))))