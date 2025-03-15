(ns esp1.fsr.static
  "Static site generation functionality.
   
   Provides a `publish-static` function to publish static site to an output directory.
   
   Also provides a `track-uri` function that can be used to track dynamically constructed URIs
   so that they can be included in static site generation."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [esp1.fsr.core :refer [clj->ns-sym clojure-file-ext get-root-ns-prefix ns-sym->uri]]
            [esp1.fsr.ring :refer [uri->endpoint-fn]]))

(def ^:dynamic tracked-uris-atom
  "Dynamically bound atom to hold tracked URIs.
   See `track-uri`."
  nil)

(defn track-uri
  "Adds URI to `tracked-uris-atom` if it exists
   so that it can be included in static site generation.
   Returns the unchanged URI."
  [uri]
  (when tracked-uris-atom
    (swap! tracked-uris-atom conj uri))
  uri)

(defn endpoint-ns-syms
  "Return all endpoint namespace symbols under the given root dir."
  [root-dir]
  (->> (file-seq (io/file root-dir))
       (filter clojure-file-ext)
       (map #(clj->ns-sym %))
       (keep identity)))

(defn generate-file
  "Evaluates the given endpoint-fn for the given URI,
   and if the return value is a string or a HTTP 200 Ok response map,
   will write the response body to out-file.
   If the return value from the function is anything else,
   will throw an Exception."
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

(defn publish-static
  "Finds all HTTP GET endpoint functions in root-fs-path, invokes them for all known URIs, and publishes their results to publish-dir.
   Known URIs are either non-parameterized URIs, or URIs tracked with `track-uri` and collected in `tracked-uris-atom`."
  [root-fs-path publish-dir]
  {:pre [(and root-fs-path publish-dir)]}
  (binding [tracked-uris-atom (atom #{})]
    (let [ns-syms (endpoint-ns-syms root-fs-path)
          uris (map #(ns-sym->uri % (get-root-ns-prefix root-fs-path)) ns-syms)]
      ;; Generate non-parameterized endpoints (& collect tracked URIs in the process)
      (doseq [uri uris]
        (when-not (re-find #"<[^/>]*>" uri) ; filter out parameterized endpoint URIs
          (when-let [endpoint-fn (uri->endpoint-fn :get uri root-fs-path)]
            (generate-file (io/file publish-dir uri "index.html") endpoint-fn uri))))
      ;; Generate tracked URIs
      (doseq [uri @tracked-uris-atom]
        (let [relative-uri (str/replace uri #"^/" "")
              endpoint-fn (uri->endpoint-fn :get uri root-fs-path)]
          (generate-file (io/file publish-dir (if (str/ends-with? relative-uri "/")
                                                (str relative-uri "index.html")
                                                relative-uri))
                         endpoint-fn uri))))))