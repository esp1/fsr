(ns esp1.fsr.compile
  "Production compilation for deployment.

   Provides unified compilation of filesystem routes into production artifacts:
   - GET routes → Static HTML files (compile-static-html)
   - Non-GET routes → Compiled route data structures (compile-dynamic-routes)

   Main entry point: `publish` function for complete production builds."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [esp1.fsr.core :refer [clj->ns-sym
                                    clojure-file-ext
                                    get-root-ns-prefix
                                    ns-endpoint-meta
                                    ns-sym->uri]]
            [esp1.fsr.ring :refer [uri->endpoint-fn]]))

;;; ============================================================================
;;; Static HTML Compilation (GET routes)
;;; ============================================================================

(def ^:dynamic *tracked-uris*
  "Dynamically bound atom to hold tracked URIs during static compilation.
   Used by `track-uri` to collect parameterized URIs for generation."
  nil)

(defn track-uri
  "Tracks a URI for static HTML generation.

   During static compilation, dynamically constructed URIs (e.g., blog post links)
   can be tracked so they are included in the generated output.

   Returns the unchanged URI, allowing inline use:
   ```clojure
   [:a {:href (track-uri (str \"/blog/\" post-id))} \"Read More\"]
   ```"
  {:malli/schema [:=> [:cat :uri-template]
                  :uri-template]}
  [uri]
  (when *tracked-uris*
    (swap! *tracked-uris* conj uri))
  uri)

(defn- generate-html-file
  "Invokes GET handler and writes response body to HTML file.

   Accepts either:
   - String response → writes directly
   - Ring response map with :status 200 → writes :body
   - Other responses → throws exception

   Creates parent directories as needed."
  [out-file endpoint-fn uri]
  (println "Generating" (.getPath out-file))
  (let [result (endpoint-fn {:uri uri})]
    (when-let [output (cond
                        (string? result)
                        result

                        (and (map? result) (= (:status result) 200))
                        (:body result)

                        :else
                        (throw
                         (Exception.
                          (str "Not generating file from endpoint function " endpoint-fn
                               " for URI " uri
                               " because the result is not a string or a HTTP 200 Ok Ring response map: "
                               (pr-str result)))))]
      (.mkdirs (.getParentFile out-file))
      (spit out-file output))))

(defn compile-static-html
  "Compiles GET routes to static HTML files.

   Finds all GET endpoint functions in root-fs-path, invokes them, and writes
   their responses to HTML files in publish-dir.

   Handles two types of routes:
   1. Non-parameterized routes (e.g., /about) - generated automatically
   2. Parameterized routes (e.g., /blog/<id>) - must be tracked via `track-uri`

   Example:
   ```clojure
   (compile-static-html \"src/routes\" \"dist\")
   ```"
  {:malli/schema [:=> [:catn
                       [:root-fs-path :dir-path]
                       [:publish-dir :dir-path]]
                  :nil]}
  [root-fs-path publish-dir]
  {:pre [(and root-fs-path publish-dir)]}
  ;; Clear cache before generation to ensure fresh content
  (esp1.fsr.core/clear-route-cache!)

  (binding [*tracked-uris* (atom #{})]
    (let [ns-syms (->> (file-seq (io/file root-fs-path))
                       (filter clojure-file-ext)
                       (map clj->ns-sym)
                       (keep identity))
          root-ns-prefix (get-root-ns-prefix root-fs-path)
          uris (map #(ns-sym->uri % root-ns-prefix) ns-syms)]

      ;; Generate non-parameterized endpoints (& collect tracked URIs in the process)
      (doseq [uri uris]
        (when-not (re-find #"<[^/>]*>" uri) ; filter out parameterized endpoint URIs
          (when-let [endpoint-fn (uri->endpoint-fn :get uri root-fs-path)]
            (generate-html-file (io/file publish-dir uri "index.html") endpoint-fn uri))))

      ;; Generate tracked URIs
      (doseq [uri @*tracked-uris*]
        (let [relative-uri (str/replace uri #"^/" "")
              endpoint-fn (uri->endpoint-fn :get uri root-fs-path)]
          (generate-html-file (io/file publish-dir (if (str/ends-with? relative-uri "/")
                                                      (str relative-uri "index.html")
                                                      relative-uri))
                              endpoint-fn uri)))

      ;; Clear cache after generation to free memory
      (esp1.fsr.core/clear-route-cache!)
      (println "Static HTML compilation complete. Cache cleared."))))

;;; ============================================================================
;;; Dynamic Route Compilation (Non-GET routes)
;;; ============================================================================

(defn discover-dynamic-routes
  "Scans root-fs-path and returns all route metadata for non-GET routes.

   Returns a sequence of route metadata maps, each containing:
   - :uri - The URI template (e.g. \"/foo\" or \"/thing/<id>\")
   - :file - The java.io.File for the route
   - :ns-sym - The namespace symbol
   - :endpoint-meta - Full namespace metadata map

   Only includes routes with :endpoint/http metadata containing non-GET methods."
  {:malli/schema [:=> [:catn
                       [:root-fs-path :dir-path]]
                  [:sequential :route-metadata]]}
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
  {:malli/schema [:=> [:cat :route-metadata] :route-metadata]}
  [route-meta]
  (let [uri (:uri route-meta)
        has-params? (re-find #"<[^/>]*>" uri)]
    (assoc route-meta :route-type (if has-params? :pattern :static))))

(defn- compile-static-dynamic-route
  "Compiles a static (non-parameterized) dynamic route into a map entry.

   'Static' here means the URI has no parameters (e.g., /api/users),
   NOT static HTML generation. These are routes for non-GET methods.

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

(defn- compile-pattern-dynamic-route
  "Compiles a parameterized dynamic route into a pattern entry.

   Parameterized routes contain <param> or <<param>> in the URI template.
   These routes require pattern matching at runtime.

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

(defn compile-dynamic-routes
  "Compiles non-GET routes into runtime-efficient data structures.

   Scans root-fs-path, discovers non-GET routes (POST/PUT/DELETE/etc.),
   classifies them as static or pattern, and compiles into efficient
   data structures for runtime matching.

   Returns:
   ```clojure
   {:static-routes {\"api/users\" {:methods {:post {...} :delete {...}}}}
    :pattern-routes [{:pattern \"^thing/([^/]+)$\" :param-names [\"id\"] ...}]}
   ```"
  {:malli/schema [:=> [:cat :dir-path] :compiled-routes]}
  [root-fs-path]
  (let [routes (discover-dynamic-routes root-fs-path)
        classified (map classify-route routes)
        {static-routes :static
         pattern-routes :pattern} (group-by :route-type classified)

        compiled-static (into {} (map compile-static-dynamic-route static-routes))
        compiled-pattern (mapv compile-pattern-dynamic-route pattern-routes)]

    {:static-routes compiled-static
     :pattern-routes compiled-pattern}))

(defn write-compiled-routes
  "Writes compiled routes to an EDN file.

   Args:
   - compiled-routes: Result from compile-routes
   - output-path: File path for output (e.g. \"dist/compiled-routes.edn\")"
  {:malli/schema [:=> [:cat :compiled-routes :file-path] :nil]}
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
  {:malli/schema [:=> [:cat [:map 
                             [:root-fs-path :dir-path] 
                             [:publish-dir :dir-path] 
                             [:compile-routes? {:optional true} :boolean] 
                             [:compiled-routes-file {:optional true} :file-path]]] 
                  :map]}
  [{:keys [root-fs-path publish-dir compile-routes? compiled-routes-file]
    :or {compile-routes? true}}]
  {:pre [(and root-fs-path publish-dir)]}

  (println "Publishing to" publish-dir)
  (println "Source:" root-fs-path)

  ;; Generate static HTML for GET routes
  (println "\n=== Compiling GET routes to static HTML ===")
  (compile-static-html root-fs-path publish-dir)

  ;; Compile non-GET routes if enabled
  (let [result {:static-files-generated :unknown}]
    (if compile-routes?
      (let [output-file (or compiled-routes-file
                            (str publish-dir "/compiled-routes.edn"))
            compiled (compile-dynamic-routes root-fs-path)]
        (println "\n=== Compiling non-GET routes to runtime data ===")
        (println "Static routes:" (count (:static-routes compiled)))
        (println "Pattern routes:" (count (:pattern-routes compiled)))
        (write-compiled-routes compiled output-file)
        (merge result
               {:compiled-routes-file output-file
                :static-routes-count (count (:static-routes compiled))
                :pattern-routes-count (count (:pattern-routes compiled))}))
      result)))
