(ns esp1.fsr.ring
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [esp1.fsr.core :refer [clj->ns-sym file-ns-name-components file->clj http-endpoint-fn ns-endpoint-meta uri->file+params]]))

(defn get-root-ns-prefix
  [root-fs-prefix]
  (string/join "." (file-ns-name-components (io/file root-fs-prefix))))

(defn uri->endpoint-fn
  [method uri root-fs-prefix]
  (let [[f path-params]
        (uri->file+params uri (io/file root-fs-prefix))

        endpoint-meta
        (-> f
            file->clj
            clj->ns-sym
            (ns-endpoint-meta (get-root-ns-prefix root-fs-prefix)))]
    (when-let [http-fn (http-endpoint-fn method endpoint-meta)]
      (fn [request]
        (http-fn (merge request
                        (-> endpoint-meta
                            (assoc :endpoint/path-params path-params))))))))

(defn wrap-fs-router
  [handler root-fs-prefix]
  (fn [{:as request
        :keys [request-method uri]}]
    (if-let [endpoint-fn (uri->endpoint-fn request-method uri root-fs-prefix)]
      ;; use endpoint-fn to generate content
      (let [response (endpoint-fn request)]
        (cond
          ;; if response is a string, treat it as an html 200 response
          (string? response)
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body response}

          ;; if response is a map, use it as the response map
          (map? response)
          response

          :else
          (throw (Exception. "Invalid response type. Endpoint function should return a string or a response map."))))

      ;; else Not Found
      (handler request))))