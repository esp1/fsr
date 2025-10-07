# FSR Project Improvement Proposals

## Overview
This document outlines proposed enhancements for the FSR (Filesystem Router) project. These improvements aim to enhance performance, developer experience, and production readiness while maintaining the project's elegant simplicity.

## 1. Performance Enhancements

### 1.2 Namespace Preloading
**Problem:** Namespaces are loaded on-demand, causing initial request delays.

**Solution:** Option to preload all route namespaces at startup.

```clojure
(defn preload-routes! 
  "Eagerly load all route namespaces at startup"
  [root-fs-path]
  (doseq [file (find-all-clj-files root-fs-path)]
    (when-let [ns-sym (clj->ns-sym file)]
      (require ns-sym))))
```

## 2. Enhanced Error Handling

### 2.1 Detailed Error Messages
**Problem:** Current error messages don't provide enough context for debugging.

**Solution:** Implement comprehensive error reporting with contextual information.

```clojure
(defn wrap-fs-router-with-errors 
  "Enhanced wrapper with detailed error reporting"
  [handler root-fs-path & {:keys [error-handler debug?]}]
  (fn [{:as request :keys [request-method uri]}]
    (try
      (if-let [endpoint-fn (uri->endpoint-fn request-method uri root-fs-path)]
        (try
          (endpoint-fn request)
          (catch Exception e
            (let [error-response {:status 500
                                  :error :handler-error
                                  :message (.getMessage e)
                                  :uri uri
                                  :method request-method}]
              (if error-handler
                (error-handler error-response e request)
                {:status 500
                 :headers {"Content-Type" "text/plain"}
                 :body (if debug?
                         (str "Handler error: " (.getMessage e) 
                              "\nURI: " uri
                              "\nMethod: " request-method
                              "\nStacktrace:\n" (with-out-str (clojure.stacktrace/print-stack-trace e)))
                         "Internal Server Error")}))))
        {:status 404
         :headers {"Content-Type" "text/plain"}
         :body (format "No route found for %s %s\nSearched in: %s" 
                       (name request-method) 
                       uri 
                       root-fs-path)})
      (catch Exception e
        {:status 500
         :headers {"Content-Type" "text/plain"}
         :body (format "Route resolution error: %s" (.getMessage e))}))))
```

### 2.2 Route Conflict Detection
**Problem:** Ambiguous routes can cause unexpected behavior.

**Solution:** Detect and warn about conflicting route patterns.

```clojure
(defn detect-route-conflicts 
  "Analyze routes for potential conflicts"
  [root-fs-path]
  (let [routes (collect-all-routes root-fs-path)
        conflicts (for [[uri-pattern files] (group-by :pattern routes)
                        :when (> (count files) 1)]
                    {:pattern uri-pattern
                     :files files})]
    (when (seq conflicts)
      (log/warn "Route conflicts detected:" conflicts))
    conflicts))
```

## 3. Development Mode Features

### 3.2 Route Inspector
**Problem:** No easy way to see all available routes.

**Solution:** Provide route inspection endpoints and CLI tools.

```clojure
(defn inspect-routes 
  "Return a data structure describing all available routes"
  [root-fs-path]
  (for [file (find-all-clj-files root-fs-path)
        :let [ns-sym (clj->ns-sym file)
              ns-meta (ns-endpoint-meta ns-sym)
              uri (ns-sym->uri ns-sym (get-root-ns-prefix root-fs-path))]]
    {:uri uri
     :file (.getPath file)
     :namespace ns-sym
     :methods (keys (:endpoint/http ns-meta))
     :params (extract-path-params (.getPath file))
     :type (:endpoint/type ns-meta)
     :metadata (dissoc ns-meta :endpoint/http :endpoint/type)}))

;; Development endpoint to show all routes
(defn routes-handler 
  "Handler that displays all available routes"
  [root-fs-path]
  (fn [request]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str (inspect-routes root-fs-path))}))
```

## 4. Route Middleware Support

### 4.1 Per-Route Middleware
**Problem:** Cannot apply middleware to specific routes without wrapping handlers.

**Solution:** Support middleware declaration in namespace metadata.

```clojure
;; Example usage in a route namespace
(ns my-app.routes.admin.users
  {:endpoint/http {:get 'list-users
                   :post 'create-user}
   :endpoint/middleware [auth-required admin-only log-access]})

;; Implementation in esp1.fsr.ring
(defn apply-route-middleware 
  "Apply middleware stack from namespace metadata"
  [handler endpoint-meta]
  (if-let [middleware (:endpoint/middleware endpoint-meta)]
    (reduce (fn [h mw-sym]
              (let [mw-fn (resolve-sym mw-sym (:endpoint/ns endpoint-meta))]
                (mw-fn h)))
            handler
            (reverse middleware))
    handler))
```

### 4.2 Global Middleware Configuration
**Problem:** No way to apply middleware to groups of routes.

**Solution:** Support middleware configuration for route prefixes.

```clojure
(defn wrap-fs-router-advanced
  "Enhanced router with middleware configuration"
  [handler config]
  ;; Config format:
  ;; {:roots {"/" {:path "src/routes"}
  ;;          "/api" {:path "src/api"
  ;;                  :middleware [wrap-json-response wrap-cors]}
  ;;          "/admin" {:path "src/admin"
  ;;                    :middleware [wrap-auth wrap-admin-check]}}}
  (fn [request]
    (let [uri (:uri request)
          matching-root (find-matching-root uri config)]
      ;; Apply appropriate middleware stack based on URI prefix
      ...)))
```

## 5. Request/Response Interceptors

### 5.1 Pre/Post Processing Hooks
**Problem:** No standard way to validate requests or transform responses.

**Solution:** Add interceptor support in namespace metadata.

```clojure
;; Example usage
(ns my-app.routes.api.users
  {:endpoint/before ['validate-api-key 'parse-request-body]
   :endpoint/after ['format-json-response 'add-cors-headers]
   :endpoint/http {:get 'get-users
                  :post 'create-user}})

;; Implementation
(defn wrap-interceptors 
  "Apply before/after interceptors to handler"
  [handler endpoint-meta]
  (fn [request]
    ;; Apply :endpoint/before interceptors
    (let [request (reduce (fn [req interceptor-sym]
                           (let [f (resolve-sym interceptor-sym 
                                               (:endpoint/ns endpoint-meta))]
                             (f req)))
                         request
                         (:endpoint/before endpoint-meta []))
          ;; Call main handler
          response (handler request)]
      ;; Apply :endpoint/after interceptors
      (reduce (fn [resp interceptor-sym]
                (let [f (resolve-sym interceptor-sym 
                                    (:endpoint/ns endpoint-meta))]
                  (f resp)))
              response
              (:endpoint/after endpoint-meta [])))))
```

## 6. Debugging and Logging

### 6.1 Request Tracing
**Problem:** Difficult to debug route resolution issues.

**Solution:** Add comprehensive logging with configurable verbosity.

```clojure
(defn wrap-request-logging 
  "Log detailed information about request handling"
  [handler & {:keys [log-level log-fn]
              :or {log-level :info
                   log-fn println}}]
  (fn [request]
    (let [start-time (System/currentTimeMillis)
          request-id (java.util.UUID/randomUUID)]
      (when (>= log-level :debug)
        (log-fn (format "[%s] Incoming: %s %s" 
                       request-id
                       (:request-method request)
                       (:uri request))))
      (let [response (handler request)
            duration (- (System/currentTimeMillis) start-time)]
        (when (>= log-level :info)
          (log-fn (format "[%s] Completed: %s %s - %s in %dms"
                         request-id
                         (:request-method request)
                         (:uri request)
                         (:status response)
                         duration)))
        response))))
```

### 6.2 Route Resolution Debugging
**Problem:** Unclear why certain routes match or don't match.

**Solution:** Add debug mode that explains route resolution.

```clojure
(defn explain-route-resolution 
  "Explain how a URI resolves to a handler"
  [uri method root-fs-path]
  (let [steps (atom [])]
    ;; Modified uri->file+params that records each step
    (let [result (debug-uri->file+params uri root-fs-path steps)]
      {:uri uri
       :method method
       :resolved? (boolean result)
       :file (first result)
       :params (second result)
       :resolution-steps @steps})))
```

## 7. Static Site Generation Enhancements

### 7.1 Parallel Generation
**Problem:** Static site generation is sequential and slow for large sites.

**Solution:** Parallelize static file generation.

```clojure
(defn publish-static-parallel 
  "Generate static site using parallel processing"
  [root-fs-path publish-dir & {:keys [parallelism]
                                :or {parallelism 4}}]
  (let [all-uris (collect-all-static-uris root-fs-path)
        chunks (partition-all (/ (count all-uris) parallelism) all-uris)]
    (doall
     (pmap (fn [uri-chunk]
             (doseq [uri uri-chunk]
               (generate-static-file uri root-fs-path publish-dir)))
           chunks))))
```

### 7.2 Incremental Builds
**Problem:** Regenerating entire site for small changes is inefficient.

**Solution:** Track file modifications and only regenerate changed content.

```clojure
(defn publish-static-incremental 
  "Only regenerate files that have changed since last build"
  [root-fs-path publish-dir]
  (let [last-build-time (read-last-build-timestamp publish-dir)
        changed-files (find-files-modified-since root-fs-path last-build-time)]
    (doseq [file changed-files]
      (let [uri (file->uri file root-fs-path)]
        (generate-static-file uri root-fs-path publish-dir)))
    (write-build-timestamp publish-dir)))
```

## 8. Framework Integration

### 8.1 Reitit Integration
**Problem:** FSR doesn't integrate with popular routing libraries.

**Solution:** Provide adapters for common frameworks.

```clojure
(defn fsr->reitit-routes 
  "Convert FSR routes to Reitit route data"
  [root-fs-path]
  (for [{:keys [uri methods params]} (inspect-routes root-fs-path)]
    [uri (into {} (for [method methods]
                    [method {:handler (partial uri->endpoint-fn method uri root-fs-path)
                            :parameters {:path (into {} (map (fn [p] [p string?]) params))}}]))]))
```

### 8.2 OpenAPI/Swagger Support
**Problem:** No API documentation generation.

**Solution:** Generate OpenAPI specs from namespace metadata.

```clojure
(ns my-app.routes.api.users
  {:endpoint/http {:get 'get-users
                   :post 'create-user}
   :endpoint/openapi {:get {:summary "List all users"
                           :responses {200 {:description "Success"
                                          :content {:application/json {:schema [:vector :User]}}}}
                     :post {:summary "Create a new user"
                           :requestBody {:content {:application/json {:schema :User}}}
                           :responses {201 {:description "Created"}}}}})
```

## 9. Route Validation

### 9.1 Compile-Time Validation
**Problem:** Route errors only discovered at runtime.

**Solution:** Add linting and validation tools.

```clojure
(defn validate-routes 
  "Validate all routes at compile time"
  [root-fs-path]
  (let [errors (atom [])]
    (doseq [file (find-all-clj-files root-fs-path)]
      ;; Check namespace can be loaded
      (try
        (let [ns-sym (clj->ns-sym file)]
          (require ns-sym)
          ;; Validate endpoint functions exist
          (let [ns-meta (ns-endpoint-meta ns-sym)]
            (doseq [[method fn-sym] (:endpoint/http ns-meta)]
              (when-not (resolve-sym fn-sym ns-sym)
                (swap! errors conj {:file file
                                   :error :missing-handler
                                   :method method
                                   :function fn-sym})))))
        (catch Exception e
          (swap! errors conj {:file file
                             :error :load-error
                             :message (.getMessage e)}))))
    @errors))
```

## 10. WebSocket Support

### 10.1 WebSocket Endpoints
**Problem:** No support for WebSocket connections.

**Solution:** Add WebSocket handler support.

```clojure
(ns my-app.routes.ws.chat
  {:endpoint/websocket 'chat-handler})

(defn chat-handler 
  "WebSocket handler for chat"
  [request]
  {:on-connect (fn [ws] ...)
   :on-message (fn [ws message] ...)
   :on-close (fn [ws status reason] ...)
   :on-error (fn [ws error] ...)})
```

## Implementation Priority

### Phase 1 (Core Improvements)
1. Route caching (1.1)
2. Enhanced error handling (2.1)
3. Route inspection tools (3.2)

### Phase 2 (Developer Experience)
4. Hot reloading (3.1)
5. Per-route middleware (4.1)
6. Request tracing (6.1)

### Phase 3 (Advanced Features)
7. Parallel static generation (7.1)
8. Framework integration (8.1)
9. WebSocket support (10.1)

## Testing Strategy

Each improvement should include:
- Unit tests for new functionality
- Integration tests with existing features
- Performance benchmarks where applicable
- Documentation updates
- Example usage in test applications

## Backward Compatibility

All improvements should maintain backward compatibility:
- New features should be opt-in via configuration
- Existing APIs should remain unchanged
- Deprecation warnings for any breaking changes
- Migration guides for major version updates

## Conclusion

These improvements would transform FSR from an elegant proof-of-concept into a production-ready routing solution while maintaining its core philosophy of filesystem-based routing. The phased approach allows for incremental adoption and testing of new features.