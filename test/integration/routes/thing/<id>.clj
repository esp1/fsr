(ns integration.routes.thing.<id>
  "Test route for /thing/:id with pattern parameters"
  {:endpoint/http {:put 'update-thing
                   :delete 'delete-thing}})

(defn update-thing [{:keys [endpoint/path-params]}]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (str "{\"id\":\"" (get path-params "id") "\",\"updated\":true}")})

(defn delete-thing [{:keys [endpoint/path-params]}]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (str "{\"id\":\"" (get path-params "id") "\",\"deleted\":true}")})
