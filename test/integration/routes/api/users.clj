(ns integration.routes.api.users
  "Test route for /api/users with POST handler"
  {:endpoint/http {:post 'create-user
                   :delete 'delete-all-users}})

(defn create-user [request]
  {:status 201
   :headers {"Content-Type" "application/json"}
   :body "{\"id\":\"123\",\"name\":\"Test User\"}"})

(defn delete-all-users [request]
  {:status 204})
