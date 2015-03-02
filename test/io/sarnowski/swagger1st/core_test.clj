(ns io.sarnowski.swagger1st.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [ring.middleware.defaults :as ring]
            [io.sarnowski.swagger1st.core :as s1st]))

(defn app [handler]
  (-> handler
      (ring/wrap-defaults ring/api-defaults)
      (s1st/swagger-routing ::s1st/yaml-cp "io/sarnowski/swagger1st/simple.yaml")))

(defn generate-greeting [request]
  "Hello!")

(deftest simple-get
  (is (= (app (mock/request :get "/hello"))
         {:status  200
          :headers {"content-type" "text/plain"}
          :body    "hello"})))