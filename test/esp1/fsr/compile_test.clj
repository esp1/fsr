(ns esp1.fsr.compile-test
  (:require [clojure.test :refer [deftest is testing]]
            [esp1.fsr.compile :refer [classify-route
                                       compile-routes
                                       compile-static-route
                                       discover-routes]]))

(deftest test-classify-route
  (testing "Static route classification"
    (let [route {:uri "/api/users"}
          result (classify-route route)]
      (is (= :static (:route-type result)))))

  (testing "Pattern route classification with single angle brackets"
    (let [route {:uri "/thing/<id>"}
          result (classify-route route)]
      (is (= :pattern (:route-type result)))))

  (testing "Pattern route classification with double angle brackets"
    (let [route {:uri "/docs/<<path>>"}
          result (classify-route route)]
      (is (= :pattern (:route-type result))))))

(deftest test-compile-static-route
  (testing "Compiles static route with POST handler"
    (let [route {:uri "/api/users"
                 :ns-sym 'test.routes.api.users
                 :endpoint-meta {:endpoint/http {:post 'create-user
                                                 :get 'list-users}
                                 :endpoint/ns 'test.routes.api.users}
                 :methods [:post]}
          [uri handler-map] (compile-static-route route)]
      (is (= "/api/users" uri))
      (is (= 'test.routes.api.users (get-in handler-map [:methods :post :ns])))
      (is (= 'create-user (get-in handler-map [:methods :post :handler]))))))

(deftest test-compile-pattern-route
  (testing "Compiles pattern route with single parameter"
    (let [route {:uri "/thing/<id>"
                 :ns-sym 'test.routes.thing.<id>
                 :endpoint-meta {:endpoint/http {:put 'update-thing
                                                 :delete 'delete-thing}
                                 :endpoint/ns 'test.routes.thing.<id>}
                 :methods [:put :delete]}
          result (#'esp1.fsr.compile/compile-pattern-route route)]
      (is (string? (:pattern result)))
      (is (= "^/thing/([^/]+)$" (:pattern result)))
      (is (= "/thing/<id>" (:uri-template result)))
      (is (= ["id"] (:param-names result)))
      (is (= 'test.routes.thing.<id> (get-in result [:methods :put :ns])))
      (is (= 'update-thing (get-in result [:methods :put :handler])))))

  (testing "Compiles pattern route with double angle brackets"
    (let [route {:uri "/docs/<<path>>"
                 :ns-sym 'test.routes.docs.<<path>>
                 :endpoint-meta {:endpoint/http {:post 'create-doc}
                                 :endpoint/ns 'test.routes.docs.<<path>>}
                 :methods [:post]}
          result (#'esp1.fsr.compile/compile-pattern-route route)]
      (is (= "^/docs/(.*)$" (:pattern result)))
      (is (= "/docs/<<path>>" (:uri-template result)))
      (is (= ["path"] (:param-names result)))
      ;; Test that pattern string compiles and matches URIs with slashes
      (is (re-matches (re-pattern (:pattern result)) "/docs/foo/bar/baz"))))

  (testing "Compiles pattern route with multiple parameters"
    (let [route {:uri "/api/users/<user-id>/posts/<post-id>"
                 :ns-sym 'test.routes.api.users.<user-id>.posts.<post-id>
                 :endpoint-meta {:endpoint/http {:delete 'delete-post}
                                 :endpoint/ns 'test.routes.api.users.<user-id>.posts.<post-id>}
                 :methods [:delete]}
          result (#'esp1.fsr.compile/compile-pattern-route route)]
      (is (= ["user-id" "post-id"] (:param-names result)))
      ;; Test pattern matching
      (let [matches (re-matches (re-pattern (:pattern result)) "/api/users/123/posts/456")]
        (is (some? matches))
        (is (= ["123" "456"] (rest matches)))))))

(comment
  ;; Manual test of route discovery
  ;; This will only work if there are actual test routes with non-GET methods

  (require '[esp1.fsr.compile :as c])
  (require '[clojure.pprint :refer [pprint]])

  ;; Discover routes in test directory
  (def discovered (c/discover-routes "test"))
  (pprint discovered)

  ;; Compile routes
  (def compiled (c/compile-routes "test"))
  (pprint compiled)

  )
