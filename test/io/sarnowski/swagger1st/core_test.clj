(ns io.sarnowski.swagger1st.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [io.sarnowski.swagger1st.core :as s1st]
            [ring.util.response :refer :all]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]))

;; infrastructure setup incl. exception handler

(defn- test-request-logging [next-fn]
  (fn [request]
    (pprint request)
    (let [response (next-fn request)]
      (print response)
      response)))

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-validator)

      (test-request-logging)

      (s1st/swagger-serializer)
      (s1st/swagger-mapper ::s1st/yaml-cp "io/sarnowski/swagger1st/user-api.yaml")))


;; application endpoints, referenced by the swagger spec

(defn read-health
  "Provides information if the system is working."
  [_]
  {:status 200})


(def user-db (atom {}))

(defn read-user
  "Reads the profile information of a user from the user-db."
  [{{:keys [id]} :parameters}]
  (-> (response (@user-db id))
      (status 200)))

(defn create-or-update-user
  "Creates or updates a profile for a user in the user-db."
  [{{:keys [id profile]} :parameters}]
  (swap! user-db assoc id profile)
  {:status 200})

(defn delete-user
  "Deletes a user from the user-db."
  [{{:keys [id]} :parameters}]
  (swap! user-db dissoc id)
  {:status 200})


;; TESTS

(deftest health

  (is (= (app (mock/request :get "/health"))
         {:status 200})))

(deftest users

  (is (= (app (-> (mock/request :post "/user/123")
                  (mock/header "Content-Type" "application/json")
                  (mock/body "sarnowski")))
         {:status 200}))

  (is (= (app (mock/request :get "/user/123"))
         {:status 200}
          :body "sarnowski"))

  (is (= (app (mock/request :delete "/user/123"))
         {:status 200})))
