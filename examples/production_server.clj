(ns production-server
  "Example production server using compiled routes.

   This server loads pre-compiled routes and uses them for request handling
   without any filesystem scanning at runtime.

   Usage:
     clojure -M -m production-server
     PORT=8080 clojure -M -m production-server"
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [esp1.fsr.runtime :refer [wrap-compiled-routes]])
  (:gen-class))

(defn not-found-handler
  "Fallback handler for routes that don't match."
  [request]
  {:status 404
   :headers {"Content-Type" "text/html"}
   :body (str "<html><body>"
              "<h1>404 Not Found</h1>"
              "<p>The requested resource was not found.</p>"
              "<p>URI: " (:uri request) "</p>"
              "</body></html>")})

(defn response-normalizer
  "Normalizes handler responses to Ring format.

   Converts:
   - String → {:status 200 :body string}
   - nil → {:status 204}
   - Ring response map → pass through"
  [response]
  (cond
    (string? response)
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body response}

    (nil? response)
    {:status 204}

    :else
    response))

(defn create-app
  "Creates the Ring application with compiled routes middleware.

   Options:
   - :compiled-routes-path - Path to compiled-routes.edn (default: dist/compiled-routes.edn)"
  [& {:keys [compiled-routes-path]
      :or {compiled-routes-path "dist/compiled-routes.edn"}}]
  (-> not-found-handler
      (wrap-compiled-routes {:compiled-routes-path compiled-routes-path
                             :response-wrapper response-normalizer})))

(defn -main
  "Start the production server.

   Environment variables:
   - PORT - Server port (default: 3000)
   - COMPILED_ROUTES - Path to compiled-routes.edn (default: dist/compiled-routes.edn)"
  [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))
        routes-path (or (System/getenv "COMPILED_ROUTES") "dist/compiled-routes.edn")]
    (println "Starting production server...")
    (println "  Port:" port)
    (println "  Compiled routes:" routes-path)
    (println)

    (let [app (create-app :compiled-routes-path routes-path)]
      (println "Server running at http://localhost:" port)
      (println "Press Ctrl+C to stop.")
      (run-jetty app {:port port
                      :join? true}))))

(comment
  ;; Start server in REPL
  (-main)

  ;; Test with custom routes file
  (def test-app (create-app :compiled-routes-path "test-routes.edn"))
  (test-app {:uri "/api/users" :request-method :post})
  )
