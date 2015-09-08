(ns {{name}}.api
  (:require [ring.util.response :as r]
            [{{name}}.db :refer [inc-counter]]))

(defn create-greeting
  "Generates a greeting message and returns it."
  [request db]
  ; extract parameter from request and generate message
  (let [name (get-in request [:parameters :query :name])
        message (str "Hello " name "!")]
    ; form ring response - json serialization will be done by s1st
    (-> (r/response {:name    name
                     :message message
                     :counter (inc-counter db)})
        (r/content-type "application/json"))))
