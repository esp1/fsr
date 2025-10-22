(ns integration.routes.index
  "Test route for GET /"
  {:endpoint/http {:get 'home-page}})

(defn home-page [request]
  "<html><body><h1>Home Page</h1></body></html>")
