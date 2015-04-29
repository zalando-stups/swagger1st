(ns io.sarnowski.swagger1st.integration
  (:require [clojure.test :refer :all]
            [ring.middleware.params :refer [wrap-params]]
            [ring.mock.request :as mock]
            [io.sarnowski.swagger1st.core-test :refer :all]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [ring.util.response :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.data.json :as json]))

;; business logic for definition

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
  (println "profile" profile)
  (swap! user-db assoc id profile)
  {:status 200})

(defn delete-user
  "Deletes a user from the user-db."
  [{{{:keys [id]} :path} :parameters}]
  (swap! user-db dissoc id)
  {:status 200})


(def app (create-app "integration.yaml" :security {"oauth2_def" (s1stsec/allow-all)
                                                   "userpw_def" (s1stsec/allow-all)}))


;; TESTS

(deftest health

         (is (= (app (mock/request :get "/.well-known/health"))
                {:status 200})))

(deftest integration

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
