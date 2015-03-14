(ns example.core
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]))

(defn create-greeting [request]
  {:status  200
   :headers {"content-type" "text/plain"}
   :body    (str "Hello " (get-in request [:parameters :path :name]) "!")})

(def app
  (-> (s1st/swagger-executor)
      (s1st/swagger-security)
      (s1st/swagger-validator)
      (s1st/swagger-parser)
      (s1st/swagger-discovery)
      (s1st/swagger-mapper ::s1st/yaml-cp "example.yaml")
      (wrap-params)))
