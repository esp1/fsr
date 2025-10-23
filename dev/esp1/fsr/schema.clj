(ns esp1.fsr.schema
  (:import [java.io File])
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [malli.core :as m]
            [malli.generator :as mg]))

(def file-name?
  (m/-simple-schema
   {:type :file-name
    :pred string?
    :type-properties {:gen/gen (gen/let [filename gen/string-alphanumeric
                                         extension (gen/elements [nil
                                                                  "clj" "cljc" "cljs" "edn"
                                                                  "c" "c++" "cpp" "h" "h++" "hpp"
                                                                  "java"
                                                                  "gif" "jpg" "png" "tiff"
                                                                  "html"
                                                                  "md" "txt"])]
                                 (str filename (when extension
                                                 (str "." extension))))}}))

(def dir-name?
  (m/-simple-schema
   {:type :dir-name
    :pred string?
    :type-properties {:gen/gen (gen/one-of [gen/string-alphanumeric
                                            (gen/elements ["." ".."])])}}))

(def dir-path?
  (m/-simple-schema
   {:type :dir-path
    :pred string?
    :type-properties {:gen/gen (gen/fmap #(str/join File/separator %)
                                         (gen/list (mg/generator dir-name?)))}}))

(def file-path?
  (m/-simple-schema
   {:type :file-path
    :pred string?
    :type-properties {:gen/gen (gen/fmap #(str/join File/separator %)
                                         (gen/let [dir-path (mg/generator dir-path?)
                                                   filename (mg/generator file-name?)]
                                           [dir-path filename]))}}))

(def file?
  (m/-simple-schema
   {:type :file
    :pred #(instance? File %)
    :type-properties {:gen/gen (gen/fmap #(File. %)
                                         (mg/generator file-path?))}}))

;; Cache schemas
(def cache-uri?
  (m/-simple-schema
   {:type :cache-uri
    :pred (fn [s] (and (string? s) (not (str/blank? s))))
    :type-properties {:gen/gen (gen/fmap #(str "/" (str/join "/" %))
                                         (gen/vector gen/string-alphanumeric 1 5))}}))

(def cache-key?
  [:map
   [:uri :string]
   [:root-path :string]])

(def cache-entry?
  [:map
   [:uri :string]
   [:root-path :string]
   [:resolved-path :string]
   [:params :map]
   [:timestamp [:int {:min 1}]]
   [:metadata {:optional true} :map]])

(def cache-config?
  [:map
   [:max-entries {:optional true :default 1000} [:int {:min 1}]]
   [:enabled? {:optional true :default true} :boolean]
   [:eviction-policy {:optional true :default :lru} [:enum :lru :fifo :none]]])

(def cache-metrics?
  [:map
   [:hits [:int {:min 0}]]
   [:misses [:int {:min 0}]]
   [:evictions [:int {:min 0}]]
   [:current-size [:int {:min 0}]]])

(defn file-schemas []
  {:file-name file-name?
   :dir-name dir-name?
   :dir-path dir-path?
   :file-path file-path?
   :file file?})

(defn cache-schemas []
  {:cache-uri cache-uri?
   :cache-key cache-key?
   :cache-entry cache-entry?
   :cache-config cache-config?
   :cache-metrics cache-metrics?}) 



;; Route and HTTP schemas
(def http-method?
  "Schema for HTTP method keywords"
  [:enum :get :post :put :delete :patch :head :options])

(def uri-template?
  "Schema for URI templates (may contain path parameters)"
  [:string {:min 1}])

(def path-params?
  "Schema for path parameters map"
  [:map-of :string :string])

(def ns-sym?
  "Schema for namespace symbols"
  :symbol)

(def handler-sym?
  "Schema for handler function symbols"
  :symbol)

(def endpoint-metadata?
  "Schema for endpoint namespace metadata"
  [:map
   [:endpoint/ns {:optional false} ns-sym?]
   [:endpoint/http {:optional true} [:map-of http-method? handler-sym?]]
   [:endpoint/type {:optional true} ns-sym?]])

(def handler-info?
  "Schema for handler information returned by route matching"
  [:map
   [:ns ns-sym?]
   [:handler handler-sym?]
   [:metadata :map]
   [:path-params {:optional true} path-params?]])

(def route-metadata?
  "Schema for route metadata during compilation"
  [:map
   [:uri uri-template?]
   [:file :file]
   [:ns-sym ns-sym?]
   [:endpoint-meta endpoint-metadata?]
   [:route-type {:optional true} [:enum :static :pattern]]])

(def compiled-static-route?
  "Schema for a compiled static (non-parameterized) route"
  [:map
   [:methods [:map-of http-method?
              [:map
               [:ns ns-sym?]
               [:handler handler-sym?]
               [:metadata :map]]]]])

(def compiled-pattern-route?
  "Schema for a compiled pattern (parameterized) route"
  [:map
   [:pattern :string]
   [:uri-template uri-template?]
   [:param-names [:vector :string]]
   [:methods [:map-of http-method?
              [:map
               [:ns ns-sym?]
               [:handler handler-sym?]
               [:metadata :map]]]]])

(def compiled-routes?
  "Schema for the complete compiled routes data structure"
  [:map
   [:static-routes [:map-of :string compiled-static-route?]]
   [:pattern-routes [:vector compiled-pattern-route?]]])

(def ring-request?
  "Schema for Ring request map (basic)"
  [:map
   [:request-method http-method?]
   [:uri :string]
   [:headers {:optional true} :map]
   [:body {:optional true} :any]])

(def ring-response?
  "Schema for Ring response map"
  [:or
   [:map
    [:status [:int {:min 100 :max 599}]]
    [:headers {:optional true} :map]
    [:body {:optional true} :any]]
   :string
   :nil])

(defn route-schemas []
  {:http-method http-method?
   :uri-template uri-template?
   :path-params path-params?
   :ns-sym ns-sym?
   :handler-sym handler-sym?
   :endpoint-metadata endpoint-metadata?
   :handler-info handler-info?
   :route-metadata route-metadata?
   :compiled-static-route compiled-static-route?
   :compiled-pattern-route compiled-pattern-route?
   :compiled-routes compiled-routes?
   :ring-request ring-request?
   :ring-response ring-response?})
