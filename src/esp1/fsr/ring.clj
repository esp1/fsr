(ns esp1.fsr.ring
  (:require [clojure.java.io :as io]
            [esp1.fsr.core :refer [clj->ns-sym
                                   file->clj
                                   get-root-ns-prefix
                                   http-endpoint-fn
                                   ns-endpoint-meta
                                   uri->file+params]]))

(defn uri->endpoint-fn
  "Returns a Ring handler function for the given HTTP method and URI
   by searching for an appropriately annotated Clojure namespace in the filesystem.
   Path parameters in the URI will be added to the handler request map argument under the :endpoing/path-params key.
   If no appropriate handler function can be found in the filesystem, returns nil."
  [method uri root-fs-prefix]
  (when-let [[f path-params] (uri->file+params uri (io/file root-fs-prefix))]
    (let [endpoint-meta (-> f
                            file->clj
                            clj->ns-sym
                            (ns-endpoint-meta (get-root-ns-prefix root-fs-prefix)))]
      (when-let [http-fn (http-endpoint-fn method endpoint-meta)]
        (fn [request]
          (http-fn (merge request
                          (-> endpoint-meta
                              (assoc :endpoint/path-params path-params)))))))))

(defn wrap-fs-router
  [handler root-fs-prefix]
  (fn [{:as request
        :keys [request-method uri]}]
    (if-let [endpoint-fn (uri->endpoint-fn request-method uri root-fs-prefix)]
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