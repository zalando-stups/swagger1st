(ns io.sarnowski.swagger1st.core-test
  (:require [clojure.test :refer :all]
            [ring.middleware.params :refer [wrap-params]]
            [ring.mock.request :as mock]
            [io.sarnowski.swagger1st.core :as s1st]
            [ring.util.response :refer :all]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.data.json :as json]))

;; infrastructure setup incl. exception handler

(defn- test-request-logging [next-fn]
  (fn [request]
    (pprint request)
    (let [response (next-fn request)]
      (print response)
      response)))

(defn ignore-security [request definition requirements]
  ; just return the request will accept the call
  request)
  ;(-> (response "Forbidden") (status 403)))

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-security {"oauth2_def" ignore-security
                              "userpw_def" ignore-security})
      (s1st/swagger-validator)

      (test-request-logging)

      (s1st/swagger-parser)
      (s1st/swagger-discovery)
      (s1st/swagger-mapper ::s1st/yaml-cp "io/sarnowski/swagger1st/user-api.yaml")

      (wrap-params)))


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
         {:status 200
          :headers {"Content-Type" "application/json"}
          :body (json/write-str {:name "sarnowski"})}))

  (is (= (app (mock/request :delete "/user/123"))
         {:status 200})))

(deftest discovery

  (is (= (app (mock/request :get "/.discovery"))
         {:status  200
          :headers {"Content-Type" "application/json"}
          :body    (json/write-str {:definition "/swagger.json"
                                    :ui         "/ui/"})})))
