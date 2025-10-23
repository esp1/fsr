(ns esp1.fsr.runtime
  "Runtime route matching for compiled routes.

   Provides efficient route matching without filesystem access by using
   pre-compiled route data structures."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn load-compiled-routes
  "Loads compiled routes from an EDN file or resource.

   Args:
   - source: Either a file path (string), java.io.File, or java.io.Reader

   Returns: Compiled routes data structure with :static-routes and :pattern-routes"
  {:malli/schema [:=> [:catn
                       [:source [:or :file-path :file :any]]]
                  :compiled-routes]}
  [source]
  (cond
    (string? source) (-> source io/reader slurp edn/read-string)
    (instance? java.io.File source) (-> source slurp edn/read-string)
    :else (edn/read-string (slurp source))))

(defn match-static-route
  "Attempts to match URI against static (non-parameterized) routes.

   Args:
   - uri: Request URI string
   - method: HTTP method keyword (e.g. :post, :put, :delete)
   - static-routes: Map from compile-routes :static-routes

   Returns: Handler info map with :ns, :handler, :metadata, or nil if no match"
  {:malli/schema [:=> [:catn
                       [:uri :uri-template]
                       [:method :http-method]
                       [:static-routes [:map-of :string :compiled-static-route]]]
                  [:maybe :handler-info]]}
  [uri method static-routes]
  (when-let [route-entry (get static-routes uri)]
    (get-in route-entry [:methods method])))

(defn match-pattern-route
  "Attempts to match URI against pattern routes (parameterized).

   Args:
   - uri: Request URI string
   - method: HTTP method keyword
   - pattern-routes: Vector from compile-routes :pattern-routes

   Returns: Handler info map with :ns, :handler, :metadata, :path-params, or nil"
  {:malli/schema [:=> [:catn
                       [:uri :uri-template]
                       [:method :http-method]
                       [:pattern-routes [:vector :compiled-pattern-route]]]
                  [:maybe :handler-info]]}
  [uri method pattern-routes]
  (reduce
    (fn [_ route]
      (when-let [handler-info (get-in route [:methods method])]
        (let [pattern (if (string? (:pattern route))
                        (re-pattern (:pattern route))
                        (:pattern route))]
          (when-let [matches (re-matches pattern uri)]
            (let [param-values (rest matches) ; First match is the full string
                  path-params (zipmap (:param-names route) param-values)]
              (reduced
                (assoc handler-info :path-params path-params)))))))
    nil
    pattern-routes))

(defn match-route
  "Top-level route matching function.

   Tries static routes first (O(1)), then pattern routes (O(n)).

   Args:
   - uri: Request URI string
   - method: HTTP method keyword
   - compiled-routes: Result from compile-routes

   Returns: Handler info map or nil if no match.
           Handler info includes:
           - :ns - Namespace symbol
           - :handler - Handler function symbol
           - :metadata - Namespace metadata
           - :path-params - Map of parameter names to values (pattern routes only)"
  {:malli/schema [:=> [:catn
                       [:uri :uri-template]
                       [:method :http-method]
                       [:compiled-routes :compiled-routes]]
                  [:maybe :handler-info]]}
  [uri method compiled-routes]
  (or (match-static-route uri method (:static-routes compiled-routes))
      (match-pattern-route uri method (:pattern-routes compiled-routes))))

(defn require-and-resolve
  "Requires namespace and resolves handler symbol to var.

   Args:
   - ns-sym: Namespace symbol
   - handler-sym: Handler function symbol (may or may not be namespaced)

   Returns: Resolved var or nil if not found"
  {:malli/schema [:=> [:catn
                       [:ns-sym :ns-sym]
                       [:handler-sym :handler-sym]]
                  :any]}
  [ns-sym handler-sym]
  (require ns-sym)
  (if (namespace handler-sym)
    (resolve handler-sym)
    (ns-resolve ns-sym handler-sym)))

(defn invoke-handler
  "Invokes a matched handler with the request.

   Args:
   - handler-info: Result from match-route
   - request: Ring request map

   Returns: Ring response (map, string, or nil)"
  {:malli/schema [:=> [:catn
                       [:handler-info :handler-info]
                       [:request :ring-request]]
                  :ring-response]}
  [handler-info request]
  (when handler-info
    (let [handler-var (require-and-resolve (:ns handler-info) (:handler handler-info))
          handler-fn (var-get handler-var)
          enriched-request (merge request
                                  (:metadata handler-info)
                                  {:endpoint/ns (:ns handler-info)
                                   :endpoint/path-params (or (:path-params handler-info) {})})]
      (handler-fn enriched-request))))

(defn wrap-compiled-routes
  "Ring middleware that routes requests using pre-compiled routes.

   This middleware is designed for production use where you want to avoid
   filesystem scanning. Routes should be compiled at build time using
   esp1.fsr.compile/compile-routes.

   Options:
   - :compiled-routes-path - Path to compiled-routes.edn file (string)
   - :compiled-routes - Pre-loaded compiled routes data structure
   - :response-wrapper - Optional fn to normalize handler responses (default: identity)

   The middleware will:
   1. Load compiled routes at initialization (once, not per request)
   2. Match incoming requests against compiled routes
   3. Invoke matched handlers with enriched request maps
   4. Pass through to next handler if no route matches

   Handler responses are passed through as-is. If you need response normalization
   (e.g., string → Ring response), provide a :response-wrapper function.

   Example:
   ```clojure
   (def app
     (-> not-found-handler
         (wrap-compiled-routes {:compiled-routes-path \"dist/compiled-routes.edn\"})))
   ```"
  {:malli/schema [:=> [:cat 
                       [:fn #(fn? %)]
                       [:map 
                        [:compiled-routes-path {:optional true} :file-path]
                        [:compiled-routes {:optional true} :compiled-routes]
                        [:response-wrapper {:optional true} [:fn #(fn? %)]]]]
                  [:fn #(fn? %)]]}
  [handler {:keys [compiled-routes-path compiled-routes response-wrapper]
            :or {response-wrapper identity}}]
  (let [routes (or compiled-routes
                   (when compiled-routes-path
                     (load-compiled-routes compiled-routes-path))
                   (throw (ex-info "Must provide either :compiled-routes or :compiled-routes-path"
                                   {})))]
    (fn [request]
      (if-let [response (some->> request
                                 :uri
                                 (#(match-route % (:request-method request) routes))
                                 (#(invoke-handler % request))
                                 response-wrapper)]
        response
        (handler request)))))
