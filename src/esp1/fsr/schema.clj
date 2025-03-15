(ns esp1.fsr.schema
  (:import [java.io File])
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [malli.generator :as mg]))

(def filename?
  [string? {:gen/gen (gen/let [filename gen/string-alphanumeric
                               extension (gen/elements [nil
                                                        "clj"
                                                        "cljc"
                                                        "cljs"
                                                        "edn"
                                                        "gif"
                                                        "html"
                                                        "java"
                                                        "jpg"
                                                        "md"
                                                        "png"
                                                        "tiff"
                                                        "txt"])]
                       (str filename (when extension
                                       (str "." extension))))}])

(def dirname?
  [string? {:gen/gen (gen/one-of [gen/string-alphanumeric
                                  (gen/elements ["." ".."])])}])

(def dir-path?
  [string? {:gen/gen (gen/fmap #(str/join File/separator %)
                               (gen/list (mg/generator dirname?)))}])

(def file-path?
  [string? {:gen/gen (gen/fmap #(str/join File/separator %)
                               (gen/let [dir-path (mg/generator dir-path?)
                                         filename (mg/generator filename?)]
                                 [dir-path filename]))}])

(def file?
  [:fn {:gen/gen (gen/fmap #(File. %)
                           (mg/generator file-path?))}
   #(instance? File %)])