(ns io.sarnowski.swagger1st.util.api
  (:require [ring.util.response :as r]
            [clojure.data.json :as json]))

; common helpers for APIs

(defn error
  "Generate an error response."
  [http-status message & [details]]
  (-> (r/response (json/write-str {:message message :details details} :escape-slash false))
      (r/content-type "application/json")
      (r/status http-status)))

(defn throw-error
  "Throws an error object."
  [http-code message & [details]]
  (throw (ex-info message {:http-code http-code
                           :message   message
                           :details   details})))

; common ring handlers for APIs

(defn add-hsts-header
  "Adds Strict-Transport-Security for HTTPS only."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (r/header response "Strict-Transport-Security" "max-age=10886400"))))

(defn add-cors-headers
  "Adds Access-Control-Allow-Headers header for common headers you might need."
  [handler & {:keys [override-options]
              :or   {override-options true}}]
  (fn [request]
    (let [response (if (and override-options
                            (= :options (:request-method request)))
                     (r/response nil)
                     (handler request))
          request-headers (get-in request [:headers "access-control-request-headers"])]
      (-> response
          (r/header "Access-Control-Allow-Origin" "*")
          (r/header "Access-Control-Max-Age" "3600")
          (r/header "Access-Control-Allow-Methods" "GET, POST, DELETE, PUT, PATCH, OPTIONS")
          (r/header "Access-Control-Allow-Headers" request-headers)))))

(defn surpress-favicon-requests
  "Returns a 404 for /favicon.ico requests."
  [handler & {:keys [favicon-path]
              :or   {favicon-path "/favicon.ico"}}]
  (fn [request]
    (if (= favicon-path (:uri request))
      (-> (r/response "No favicon available.")
          (r/status 404))
      (handler request))))
