#!/usr/bin/env bb

(ns build
  "Build script for publishing fsr application to production.

   Usage:
     bb build.clj                    # Publish to default 'dist' directory
     bb build.clj --output ./public  # Publish to custom directory"
  (:require [clojure.tools.cli :refer [parse-opts]]
            [esp1.fsr.compile :refer [publish]]))

(def cli-options
  [["-o" "--output DIR" "Output directory for published files"
    :default "dist"]
   ["-r" "--routes DIR" "Root directory containing route files"
    :default "src/routes"]
   ["-h" "--help" "Show this help message"]])

(defn -main [& args]
  (let [{:keys [options summary errors]} (parse-opts args cli-options)]
    (cond
      (:help options)
      (do
        (println "Build Script - fsr Static Site + Route Compilation")
        (println)
        (println summary)
        (System/exit 0))

      errors
      (do
        (println "Errors:")
        (doseq [error errors]
          (println "  " error))
        (System/exit 1))

      :else
      (let [{:keys [output routes]} options]
        (println "Building fsr application...")
        (println "  Routes:" routes)
        (println "  Output:" output)
        (println)

        (let [result (publish {:root-fs-path routes
                               :publish-dir output
                               :compile-routes? true})]
          (println)
          (println "✓ Build complete!")
          (println "  Compiled routes:" (:compiled-routes-file result))
          (println "  Static routes:" (:static-routes-count result))
          (println "  Pattern routes:" (:pattern-routes-count result))
          (println)
          (println "Deploy the following to production:")
          (println "  - Static HTML files:" output "/")
          (println "  - Compiled routes:" (:compiled-routes-file result))
          (println "  - Handler namespaces (as compiled .class files)")
          (System/exit 0))))))

;; Run when executed as script
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
