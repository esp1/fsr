(ns esp1.fsr.schema
  (:import [java.io File])
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [malli.core :as m]
            [malli.generator :as mg]))

(def filename?
  (m/-simple-schema
   {:type :filename
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

(def dirname?
  (m/-simple-schema
   {:type :dirname
    :pred string?
    :type-properties {:gen/gen (gen/one-of [gen/string-alphanumeric
                                            (gen/elements ["." ".."])])}}))

(def dir-path?
  (m/-simple-schema
   {:type :dir-path
    :pred string?
    :type-properties {:gen/gen (gen/fmap #(str/join File/separator %)
                                         (gen/list (mg/generator dirname?)))}}))

(def file-path?
  (m/-simple-schema
   {:type :file-path
    :pred string?
    :type-properties {:gen/gen (gen/fmap #(str/join File/separator %)
                                         (gen/let [dir-path (mg/generator dir-path?)
                                                   filename (mg/generator filename?)]
                                           [dir-path filename]))}}))

(def file?
  (m/-simple-schema
   {:type :file
    :pred #(instance? File %)
    :type-properties {:gen/gen (gen/fmap #(File. %)
                                         (mg/generator file-path?))}}))