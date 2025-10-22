(ns esp1.fsr.runtime-test
  (:require [clojure.test :refer [deftest is testing]]
            [esp1.fsr.runtime :refer [invoke-handler
                                       load-compiled-routes
                                       match-pattern-route
                                       match-route
                                       match-static-route
                                       wrap-compiled-routes]]))

;; Test handler functions
(defn test-post-handler [request]
  {:status 200
   :body (str "Created: " (get-in request [:endpoint/path-params "id"]))})

(defn test-delete-handler [request]
  {:status 204})

(def sample-compiled-routes
  {:static-routes
   {"/api/users" {:methods {:post {:ns 'esp1.fsr.runtime-test
                                   :handler 'test-post-handler
                                   :metadata {}}}}}

   :pattern-routes
   [{:pattern "^/thing/([^/]+)$"
     :uri-template "/thing/<id>"
     :param-names ["id"]
     :methods {:delete {:ns 'esp1.fsr.runtime-test
                        :handler 'test-delete-handler
                        :metadata {}}}}]})

(deftest test-match-static-route
  (testing "Matches static route with correct method"
    (let [result (match-static-route "/api/users" :post (:static-routes sample-compiled-routes))]
      (is (some? result))
      (is (= 'esp1.fsr.runtime-test (:ns result)))
      (is (= 'test-post-handler (:handler result)))))

  (testing "Returns nil for static route with wrong method"
    (let [result (match-static-route "/api/users" :get (:static-routes sample-compiled-routes))]
      (is (nil? result))))

  (testing "Returns nil for non-existent static route"
    (let [result (match-static-route "/api/posts" :post (:static-routes sample-compiled-routes))]
      (is (nil? result)))))

(deftest test-match-pattern-route
  (testing "Matches pattern route and extracts parameters"
    (let [result (match-pattern-route "/thing/123" :delete (:pattern-routes sample-compiled-routes))]
      (is (some? result))
      (is (= 'esp1.fsr.runtime-test (:ns result)))
      (is (= 'test-delete-handler (:handler result)))
      (is (= {"id" "123"} (:path-params result)))))

  (testing "Returns nil for pattern route with wrong method"
    (let [result (match-pattern-route "/thing/123" :post (:pattern-routes sample-compiled-routes))]
      (is (nil? result))))

  (testing "Returns nil for non-matching pattern"
    (let [result (match-pattern-route "/other/123" :delete (:pattern-routes sample-compiled-routes))]
      (is (nil? result)))))

(deftest test-match-route
  (testing "Matches static route"
    (let [result (match-route "/api/users" :post sample-compiled-routes)]
      (is (some? result))
      (is (= 'test-post-handler (:handler result)))))

  (testing "Matches pattern route"
    (let [result (match-route "/thing/456" :delete sample-compiled-routes)]
      (is (some? result))
      (is (= 'test-delete-handler (:handler result)))
      (is (= {"id" "456"} (:path-params result)))))

  (testing "Returns nil when no routes match"
    (let [result (match-route "/nonexistent" :get sample-compiled-routes)]
      (is (nil? result)))))

(deftest test-invoke-handler
  (testing "Invokes handler with enriched request"
    (let [handler-info {:ns 'esp1.fsr.runtime-test
                        :handler 'test-post-handler
                        :metadata {:some "metadata"}
                        :path-params {"id" "789"}}
          request {:uri "/thing/789" :request-method :post}
          response (invoke-handler handler-info request)]
      (is (= 200 (:status response)))
      (is (= "Created: 789" (:body response)))))

  (testing "Returns nil for nil handler-info"
    (let [response (invoke-handler nil {:uri "/foo"})]
      (is (nil? response)))))

(deftest test-wrap-compiled-routes
  (testing "Matches and invokes handler for static route"
    (let [fallback-handler (fn [req] {:status 404 :body "Not found"})
          app (wrap-compiled-routes fallback-handler {:compiled-routes sample-compiled-routes})
          request {:uri "/api/users" :request-method :post}
          response (app request)]
      (is (= 200 (:status response)))))

  (testing "Matches and invokes handler for pattern route"
    (let [fallback-handler (fn [req] {:status 404 :body "Not found"})
          app (wrap-compiled-routes fallback-handler {:compiled-routes sample-compiled-routes})
          request {:uri "/thing/999" :request-method :delete}
          response (app request)]
      (is (= 204 (:status response)))))

  (testing "Falls through to next handler when no route matches"
    (let [fallback-handler (fn [req] {:status 404 :body "Not found"})
          app (wrap-compiled-routes fallback-handler {:compiled-routes sample-compiled-routes})
          request {:uri "/nonexistent" :request-method :get}
          response (app request)]
      (is (= 404 (:status response)))
      (is (= "Not found" (:body response))))))

(deftest test-load-compiled-routes
  (testing "Loads compiled routes from string representation"
    (let [routes-str (pr-str sample-compiled-routes)
          temp-file (java.io.File/createTempFile "compiled-routes" ".edn")]
      (try
        (spit temp-file routes-str)
        (let [loaded (load-compiled-routes (.getPath temp-file))]
          (is (= sample-compiled-routes loaded)))
        (finally
          (.delete temp-file))))))
