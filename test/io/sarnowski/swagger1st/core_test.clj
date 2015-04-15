(ns io.sarnowski.swagger1st.core-test
  (:require [clojure.test :refer :all]
            [ring.middleware.params :refer [wrap-params]]
            [ring.mock.request :as mock]
            [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.security :as s1stsec]
            [ring.util.response :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.data.json :as json]))

;; infrastructure setup incl. exception handler

(defn- test-request-logging [next-handler]
  (fn [request]
    (pprint request)
    (let [response (next-handler request)]
      (print response)
      response)))

(def app
  (-> (s1st/swagger-context ::s1st/yaml-cp "io/sarnowski/swagger1st/user-api.yaml")
      (s1st/swagger-ring wrap-params)
      (s1st/swagger-mapper)
      (s1st/swagger-discovery)
      (s1st/swagger-parser)
      (s1st/swagger-ring test-request-logging)
      (s1st/swagger-validator)
      (s1st/swagger-security {"oauth2_def" (s1stsec/allow-all)
                              "userpw_def" (s1stsec/allow-all)})
      (s1st/swagger-executor)))


;; application endpoints, referenced by the swagger spec

(defn read-health
  "Provides information if the system is working."
  [_]
  {:status 200})


(def user-db (atom {}))

(defn read-user
  "Reads the profile information of a user from the user-db."
  [{{{:keys [id]} :path} :parameters}]
  (-> (response (@user-db id))
      (header "Content-Type" "application/json")
      (status 200)))

(defn create-or-update-user
  "Creates or updates a profile for a user in the user-db."
  [{{{:keys [id]} :path {:keys [profile]} :body} :parameters}]
  (swap! user-db assoc id profile)
  {:status 200})

(defn delete-user
  "Deletes a user from the user-db."
  [{{{:keys [id]} :path} :parameters}]
  (swap! user-db dissoc id)
  {:status 200})


;; TESTS

(deftest health

  (is (= (app (mock/request :get "/health"))
         {:status 200})))

(deftest users

  (is (= (app (-> (mock/request :post "/user/123")
                  (mock/header "Content-Type" "application/json; charset=UTF-8")
                  (mock/body (json/write-str {:name "sarnowski"}))))
         {:status 200}))

  (is (= (app (mock/request :get "/user/123"))
         {:status  200
          :headers {"Content-Type" "application/json"}
          :body    (json/write-str {:name "sarnowski"})}))

  (is (= (app (mock/request :delete "/user/123"))
         {:status 200})))

(deftest discovery

  (let [result (app (mock/request :get "/.well-known/schema-discovery"))
        discovery (json/read-str (:body result))]
    (is (= discovery)
        {:schema_url  "/swagger.json"
         :schema_type "swagger-2.0"
         :ui_url      "/ui/"})))
