(ns esp1.fsr.schema
  (:import [java.io File]))

(def file?
  [:fn #(instance? File %)])
