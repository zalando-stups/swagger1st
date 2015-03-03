(ns example.core
  (:require [ring.middleware.defaults :as ring]
            [io.sarnowski.swagger1st.core :as s1st]))

(defn create-greeting [request]
  "Hello!")

(defn app [handler]
  (-> handler
    (ring/wrap-defaults ring/api-defaults)
    (s1st/swagger-routing ::s1st/yaml-cp "example.yaml")))
