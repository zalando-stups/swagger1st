(ns io.sarnowski.swagger1st.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [ring.middleware.defaults :as ring]
            [io.sarnowski.swagger1st.core :as s1st]))

(def app
  (-> (s1st/swagger-executor)

      (ring/wrap-defaults ring/api-defaults)
      (s1st/swagger-mapper ::s1st/yaml-cp "io/sarnowski/swagger1st/simple.yaml")
      (s1st/swagger-validator)))


(defn generate-greeting [request]
  {:status  200
   :headers {"content-type" "text/plain"}
   :body    "Hello!"})


(deftest simple-get
  (is (= (app (mock/request :post "/greeting"))
         {:status  200
          :headers {"content-type" "text/plain"}
          :body    "Hello!"})))
