(ns example.core
  (:require [ring.middleware.defaults :as ring]
            [io.sarnowski.swagger1st.core :as s1st]))

(defn create-greeting [request]
  {:status  200
   :headers {"content-type" "text/plain"}
   :body    "Hello!"})

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-validator)
      (s1st/swagger-mapper ::s1st/yaml-cp "example.yaml")
      (ring/wrap-defaults ring/api-defaults)))
