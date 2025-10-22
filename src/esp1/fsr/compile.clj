(ns esp1.fsr.compile
  "Production compilation for deployment.

   Provides unified compilation of both:
   - Static HTML generation for GET routes (delegates to esp1.fsr.static)
   - Route compilation for non-GET routes (compiled route matching)

   Main entry point: `publish` function for complete production builds."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [esp1.fsr.core :refer [clj->ns-sym
                                    clojure-file-ext
                                    get-root-ns-prefix
                                    ns-endpoint-meta
                                    ns-sym->uri]]
            [esp1.fsr.static :as static]))

(defn discover-routes
  "Scans root-fs-path and returns all route metadata for non-GET routes.

   Returns a sequence of route metadata maps, each containing:
   - :uri - The URI template (e.g. \"/foo\" or \"/thing/<id>\")
   - :file - The java.io.File for the route
   - :ns-sym - The namespace symbol
   - :endpoint-meta - Full namespace metadata map

   Only includes routes with :endpoint/http metadata containing non-GET methods."
  [root-fs-path]
  (let [root-ns-prefix (get-root-ns-prefix root-fs-path)]
    (->> (file-seq (io/file root-fs-path))
         (filter clojure-file-ext)  ; Filter files first, before file->clj
         (map (fn [file]
                (when-let [ns-sym (clj->ns-sym file)]
                  (let [endpoint-meta (ns-endpoint-meta ns-sym)
                        http-methods (keys (:endpoint/http endpoint-meta))
                        non-get-methods (remove #{:get} http-methods)]
                    (when (seq non-get-methods)
                      {:uri (ns-sym->uri ns-sym root-ns-prefix)
                       :file file
                       :ns-sym ns-sym
                       :endpoint-meta endpoint-meta
                       :methods non-get-methods})))))
         (keep identity))))

(defn classify-route
  "Classifies a route as :static or :pattern based on its URI template.

   - :static - No path parameters (e.g. \"/api/users\")
   - :pattern - Contains <param> or <<param>> (e.g. \"/thing/<id>\")

   Returns the route metadata with added :route-type key."
  [route-meta]
  (let [uri (:uri route-meta)
        has-params? (re-find #"<[^/>]*>" uri)]
    (assoc route-meta :route-type (if has-params? :pattern :static))))

(defn compile-static-route
  "Compiles a static route (no parameters) into a map entry.

   Returns a 2-tuple: [uri-string {:methods {method handler-info}}]

   Handler info contains:
   - :ns - Namespace symbol
   - :handler - Handler function symbol
   - :metadata - Full namespace metadata"
  [route-meta]
  (let [uri (:uri route-meta)
        ns-sym (:ns-sym route-meta)
        endpoint-meta (:endpoint-meta route-meta)
        methods (:methods route-meta)
        method-map (reduce
                     (fn [acc method]
                       (if-let [handler-sym (get-in endpoint-meta [:endpoint/http method])]
                         (assoc acc method {:ns ns-sym
                                            :handler handler-sym
                                            :metadata endpoint-meta})
                         acc))
                     {}
                     methods)]
    [uri {:methods method-map}]))

(defn- uri->pattern-and-params
  "Converts a URI template with path parameters into a regex pattern string and param names.

   Examples:
   - \"/thing/<id>\" → [\"^/thing/([^/]+)$\" [\"id\"]]
   - \"/docs/<<path>>\" → [\"^/docs/(.*)$\" [\"path\"]]
   - \"/api/users/<id>/posts/<post-id>\" → [\"^/api/users/([^/]+)/posts/([^/]+)$\" [\"id\" \"post-id\"]]

   Returns: [pattern-string param-names-vector]
   Note: Pattern is returned as string for EDN serialization. Runtime will compile it."
  [uri-template]
  (let [matcher (re-matcher #"<<([^<>]+)>>|<([^<>]+)>" uri-template)
        matches (loop [m (re-find matcher)
                       acc []]
                  (if m
                    (recur (re-find matcher) (conj acc m))
                    acc))
        param-names (mapv #(or (nth % 1 nil) (nth % 2 nil)) matches)
        pattern-str (-> uri-template
                        (str/replace #"<<([^<>]+)>>" "(.*)")
                        (str/replace #"<([^<>]+)>" "([^/]+)"))]
    [(str "^" pattern-str "$") param-names]))

(defn compile-pattern-route
  "Compiles a parameterized route into a pattern entry.

   Returns a map containing:
   - :pattern - Regex pattern string (not compiled, for EDN serialization)
   - :uri-template - Original URI template (for debugging)
   - :param-names - Vector of path parameter names
   - :methods - Map of HTTP methods to handler info"
  [route-meta]
  (let [uri-template (:uri route-meta)
        ns-sym (:ns-sym route-meta)
        endpoint-meta (:endpoint-meta route-meta)
        methods (:methods route-meta)
        [pattern-str param-names] (uri->pattern-and-params uri-template)
        method-map (reduce
                     (fn [acc method]
                       (if-let [handler-sym (get-in endpoint-meta [:endpoint/http method])]
                         (assoc acc method {:ns ns-sym
                                            :handler handler-sym
                                            :metadata endpoint-meta})
                         acc))
                     {}
                     methods)]
    {:pattern pattern-str
     :uri-template uri-template
     :param-names param-names
     :methods method-map}))

(defn compile-routes
  "Main compilation function.

   Scans root-fs-path, discovers non-GET routes, classifies them, and
   compiles into efficient data structures.

   Returns:
   {:static-routes {uri {:methods {method handler-info}}}
    :pattern-routes [{:pattern regex :uri-template string ...}]}"
  [root-fs-path]
  (let [routes (discover-routes root-fs-path)
        classified (map classify-route routes)
        {static-routes :static
         pattern-routes :pattern} (group-by :route-type classified)

        compiled-static (into {} (map compile-static-route static-routes))
        compiled-pattern (mapv compile-pattern-route pattern-routes)]

    {:static-routes compiled-static
     :pattern-routes compiled-pattern}))

(defn write-compiled-routes
  "Writes compiled routes to an EDN file.

   Args:
   - compiled-routes: Result from compile-routes
   - output-path: File path for output (e.g. \"dist/compiled-routes.edn\")"
  [compiled-routes output-path]
  (io/make-parents output-path)
  (spit output-path (pr-str compiled-routes))
  (println "Wrote compiled routes to" output-path))

(defn publish
  "Unified publishing function for production deployment.

   Generates both static HTML files (for GET routes) and compiled route data (for non-GET routes).
   This is the recommended function for production builds.

   Options map:
   - :root-fs-path - Source directory containing route files (required)
   - :publish-dir - Output directory for generated files (required)
   - :compile-routes? - Enable route compilation for non-GET methods (default: true)
   - :compiled-routes-file - Output file for compiled routes (default: publish-dir/compiled-routes.edn)

   Returns a map with:
   - :static-files-generated - Number of static HTML files generated
   - :compiled-routes-file - Path to compiled routes file (if enabled)
   - :static-routes-count - Number of static routes compiled
   - :pattern-routes-count - Number of pattern routes compiled

   Example:
   ```clojure
   (publish {:root-fs-path \"src/my_app/routes\"
             :publish-dir \"dist\"
             :compile-routes? true})
   ```"
  [{:keys [root-fs-path publish-dir compile-routes? compiled-routes-file]
    :or {compile-routes? true}}]
  {:pre [(and root-fs-path publish-dir)]}

  (println "Publishing to" publish-dir)
  (println "Source:" root-fs-path)

  ;; Generate static HTML for GET routes
  (println "\n=== Generating static HTML files ===")
  (static/publish-static root-fs-path publish-dir)

  ;; Compile non-GET routes if enabled
  (let [result {:static-files-generated :unknown}] ; publish-static doesn't return count yet
    (if compile-routes?
      (let [output-file (or compiled-routes-file
                            (str publish-dir "/compiled-routes.edn"))
            compiled (compile-routes root-fs-path)]
        (println "\n=== Compiling non-GET routes ===")
        (println "Static routes:" (count (:static-routes compiled)))
        (println "Pattern routes:" (count (:pattern-routes compiled)))
        (write-compiled-routes compiled output-file)
        (merge result
               {:compiled-routes-file output-file
                :static-routes-count (count (:static-routes compiled))
                :pattern-routes-count (count (:pattern-routes compiled))}))
      (do
        (println "\n=== Route compilation disabled ===")
        result))))
