(ns helloworld
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.util.response :as r]))

;; your business logic (see resources/helloworld-api.yaml)

(defn create-greeting
  "Generates a greeting message and returns it."
  [request]
  ; extract parameter from request and generate message
  (let [name (get-in request [:parameters :query :name])
        message (str "Hello " name "!")]
    ; form ring response - json serialization will be done by s1st
    (-> (r/response {:name    name
                     :message message})
        (r/content-type "application/json"))))


;; swagger1st setup (ring handler)

(def app
  (-> (s1st/context :yaml-cp "helloworld-api.yaml")
      (s1st/discoverer)
      (s1st/mapper)
      (s1st/parser)
      (s1st/executor)))