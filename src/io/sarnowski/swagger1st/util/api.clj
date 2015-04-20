(ns io.sarnowski.swagger1st.util.api
  (:require [ring.util.response :as r]
            [clojure.data.json :as json]))

; common helpers for APIs

; TODO surpress favicon
; TODO supports CORS headers
; TODO provide OPTIONS for all paths

(comment

  (fn [request]
    (cond
      (and surpress-favicon (= "/favicon.ico" (:uri request)))
      (-> (r/response "No favicon available.")
          (r/status 404))

      (and cors-origin (= :options (:request-method request)))
      (-> (r/response nil)
          (r/status 200)
          (add-cors-headers cors-origin))))

  )

(defn- cors-headers [response]
  (-> response
      (r/header "Access-Control-Allow-Origin" "*")
      (r/header "Access-Control-Max-Age" "3600")
      (r/header "Access-Control-Allow-Methods" "GET, POST, DELETE, PUT, PATCH, OPTIONS")
      (r/header "Access-Control-Allow-Headers" "*")))

(defn error
  "Generate an error response."
  [http-status message & [detail]]
  (-> (r/response (json/write-str {:message message :detail detail} :escape-slash false))
      (r/content-type "application/json")
      (r/status http-status)))