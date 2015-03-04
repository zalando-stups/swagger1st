(ns io.sarnowski.swagger1st.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [ring.middleware.defaults :as ring]
            [io.sarnowski.swagger1st.core :as s1st]
            [ring.util.response :refer :all]
            [clojure.tools.logging :as log]))

(defn- transform-exception [next-fn]
  (fn [request]
    (try
      (next-fn request)
      (catch Exception e
        {:status 500
         :headers {"Content-Type" "plain/text"}
         :body (.toString e)}))))

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-validator)
      (s1st/swagger-mapper ::s1st/yaml-cp "io/sarnowski/swagger1st/simple.yaml")

      (transform-exception)

      (ring/wrap-defaults ring/api-defaults)))


(defn generate-greeting [request]
  (-> (response "Hello!")
      (content-type "application/json")))

(defn provide-health-status [request]
  (-> (response true)
      (content-type "application/json")))


(deftest simple-get

  (is (= (app (mock/request :post "/greeting"))
         (-> (response "Hello!")
             (content-type "application/json"))))

  (is (= (app (mock/request :get "/health"))
         (-> (response true)
             (content-type "application/json")))))
