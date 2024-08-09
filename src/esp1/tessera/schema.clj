(ns esp1.tessera.schema
  (:import [java.io File]))

(def file?
  [:fn #(instance? File %)])
