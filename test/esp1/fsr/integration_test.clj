(ns esp1.fsr.integration-test
  "End-to-end integration test for production route compilation.

   Tests the complete flow:
   1. Compile routes from filesystem
   2. Write to EDN file
   3. Load compiled routes
   4. Match and invoke handlers via middleware"
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [esp1.fsr.compile :refer [compile-routes write-compiled-routes publish]]
            [esp1.fsr.runtime :refer [load-compiled-routes wrap-compiled-routes]]))

(def test-routes-dir "test/integration/routes")
(def temp-output-dir (str "/tmp/fsr-test-" (System/currentTimeMillis)))

(defn cleanup-fixture [f]
  "Clean up temp directory after tests"
  (try
    (f)
    (finally
      ;; Clean up temp directory
      (when (.exists (io/file temp-output-dir))
        (doseq [file (reverse (file-seq (io/file temp-output-dir)))]
          (.delete file))))))

(use-fixtures :once cleanup-fixture)

(deftest test-full-compilation-flow
  (testing "Compile routes from test directory"
    (let [compiled (compile-routes test-routes-dir)]
      (is (map? compiled))
      (is (contains? compiled :static-routes))
      (is (contains? compiled :pattern-routes))

      ;; Should have compiled the POST and DELETE routes
      ;; Note: URIs from ns-sym->uri don't have leading slash
      (is (contains? (:static-routes compiled) "api/users"))
      (is (get-in compiled [:static-routes "api/users" :methods :post]))
      (is (get-in compiled [:static-routes "api/users" :methods :delete]))

      ;; Should have compiled the pattern route
      (is (seq (:pattern-routes compiled)))
      (let [pattern-route (first (:pattern-routes compiled))]
        (is (= "thing/<id>" (:uri-template pattern-route)))
        (is (= ["id"] (:param-names pattern-route)))
        (is (get-in pattern-route [:methods :put]))
        (is (get-in pattern-route [:methods :delete])))))

  (testing "Write and load compiled routes"
    (let [compiled (compile-routes test-routes-dir)
          output-file (str temp-output-dir "/compiled-routes.edn")]
      (write-compiled-routes compiled output-file)

      ;; Verify file was created
      (is (.exists (io/file output-file)))

      ;; Load it back
      (let [loaded (load-compiled-routes output-file)]
        (is (= compiled loaded)))))

  (testing "Invoke handlers via middleware"
    (let [compiled (compile-routes test-routes-dir)
          fallback (fn [_] {:status 404 :body "Not found"})
          app (wrap-compiled-routes fallback {:compiled-routes compiled})]

      ;; Test static route (POST api/users) - no leading slash in compiled URIs
      (let [response (app {:uri "api/users" :request-method :post})]
        (is (= 201 (:status response)))
        (is (= "{\"id\":\"123\",\"name\":\"Test User\"}" (:body response))))

      ;; Test static route (DELETE api/users)
      (let [response (app {:uri "api/users" :request-method :delete})]
        (is (= 204 (:status response))))

      ;; Test pattern route (PUT thing/456) - no leading slash
      (let [response (app {:uri "thing/456" :request-method :put})]
        (is (= 200 (:status response)))
        (is (.contains (:body response) "456"))
        (is (.contains (:body response) "updated")))

      ;; Test pattern route (DELETE thing/789)
      (let [response (app {:uri "thing/789" :request-method :delete})]
        (is (= 200 (:status response)))
        (is (.contains (:body response) "789"))
        (is (.contains (:body response) "deleted")))

      ;; Test non-matching route falls through
      (let [response (app {:uri "/nonexistent" :request-method :get})]
        (is (= 404 (:status response)))
        (is (= "Not found" (:body response)))))))

;; Note: Full publish integration tests would require GET handler resolution
;; which needs proper namespace setup. The core compilation and runtime
;; functionality is tested above without static site generation.
