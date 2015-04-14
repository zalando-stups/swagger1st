(ns helloworld
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]))

;; your business logic (see resources/helloworld-api.yaml)

(defn create-greeting [request]
  {:status  200
   :headers {"content-type" "text/plain"}
   :body    (str "Hello " (get-in request [:parameters :path :name]) "!")})


;; swagger1st setup (ring handler)

(def app
  (-> (s1st/swagger-context ::s1st/yaml-cp "helloworld-api.yaml")
      (s1st/swagger-ring wrap-params)
      (s1st/swagger-mapper)
      (s1st/swagger-discovery)
      (s1st/swagger-parser)
      (s1st/swagger-validator)
      (s1st/swagger-executor)))
