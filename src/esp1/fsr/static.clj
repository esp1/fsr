(ns esp1.fsr.static
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [esp1.fsr.core :refer [clj->ns-sym get-root-ns-prefix ns-sym->uri]]
            [esp1.fsr.ring :refer [uri->endpoint-fn]]
            [esp1.fsr.track-uri :refer [tracked-uris-atom]]))

(defn endpoint-ns-syms
  "Return all endpoint namespace symbols under the given root dir."
  [root-dir]
  (->> (file-seq (io/file root-dir))
       (filter #(string/ends-with? (.getName %) ".clj"))
       (map #(clj->ns-sym %))
       (keep identity)))

(defn generate-file
  [out-file endpoint-fn uri]
  (println "Generating" (.getPath out-file))
  (.mkdirs (.getParentFile out-file))
  (spit out-file (endpoint-fn {:uri uri})))

(defn publish-static
  "Finds all HTTP GET endpoint functions in root-fs-prefix, invokes them for all known URIs, and publishes their results to publish-dir.
   Known URIs are either non-parameterized URIs, or URIs tracked with `esp1.fsr.track-uri/track-uri` and collected in `esp1.fsr.track-uri/tracked-uris-atom`."
  [root-fs-prefix publish-dir]
  {:pre [(and root-fs-prefix publish-dir)]}
  (binding [tracked-uris-atom (atom #{})]
    (let [ns-syms (endpoint-ns-syms root-fs-prefix)
          uris (map #(ns-sym->uri % (get-root-ns-prefix root-fs-prefix)) ns-syms)]
      ;; Generate non-parameterized endpoints (& collect tracked URIs in the process)
      (doseq [uri uris]
        (when-not (re-find #"<[^/>]*>" uri) ; filter out parameterized endpoint URIs
          (when-let [endpoint-fn (uri->endpoint-fn :get uri root-fs-prefix)]
            (generate-file (io/file publish-dir uri "index.html") endpoint-fn uri))))
      ;; Generate tracked URIs
      (doseq [uri @tracked-uris-atom]
        (let [relative-uri (string/replace uri #"^/" "")
              endpoint-fn (uri->endpoint-fn :get uri root-fs-prefix)]
          (generate-file (io/file publish-dir (if (string/ends-with? relative-uri "/")
                                                (str relative-uri "index.html")
                                                relative-uri))
                         endpoint-fn uri))))))