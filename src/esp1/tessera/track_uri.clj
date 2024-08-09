(ns esp1.tessera.track-uri
  (:require [taoensso.telemere :as t]))

(def ^:dynamic tracked-uris-atom
  "Dynamically bound atom to hold tracked URIs.
   See `track-uri`."
  nil)

(defn track-uri
  "Adds URI to `tracked-uris-atom` if it exists."
  [uri]
  (t/log! ["track-uri:" uri])
  (when tracked-uris-atom
    (swap! tracked-uris-atom conj uri))
  uri)
