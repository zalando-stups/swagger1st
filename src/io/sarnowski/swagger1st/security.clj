(ns io.sarnowski.swagger1st.security
  (:require [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [clj-http.lite.client :as client]
            [ring.util.response :as ring]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

;; functions that generate security handler

(defn allow-all
  "Allows everything."
  []
  (fn [request definition requirements]
    request))

(defn basic-auth
  "Checks basic auth."
  [check-fn]
  (let [handler (wrap-basic-authentication (fn [request] request) check-fn)]
    (fn [request definition requirements]
      (handler request))))

(defn- extract-token
  "Extracts the bearer token from the Authorization header."
  [request]
  (if-let [authorization (get-in request :headers "authorization")]
    (when (.startsWith authorization "Bearer ")
      (.substring authorization (count "Bearer ")))))

(defn- deny-response
  "Send forbidden response."
  [request reason]
  (log/warn "Access denied on %s because %s." (select-keys request [:remote-addr :request-method :uri :headers]) reason)
  (-> (ring/response "Forbidden")
      (ring/status 403)))

(defn tokeninfo-scope-attribute
  "Checks if every scope is mentioned in the 'scope' attribute of the response."
  [tokeninfo scopes]
  (let [available-scopes (into #{} (get tokeninfo "scope"))]
    (every? available-scopes scopes)))

(defn oauth-2.0
  "Checks OAuth 2.0 tokens. config-fn returns a map with the following configuration options:
    :tokeninfo-url"
  [get-config-fn check-scopes-fn]
  (fn [request definition requirements]
    (if-let [token (extract-token request)]
      (let [config (get-config-fn)
            tokeninfo-url (:tokeninfo-url config)]
        (if tokeninfo-url
          (let [result (client/get tokeninfo-url
                                   {:query-params     {"access_token" token}
                                    :throw-exceptions false})]
            (if (= 200 (:status result))
              (let [tokeninfo (json/read-str (:body result))]
                (if (check-scopes-fn tokeninfo requirements)
                  request
                  (deny-response request "scopes not granted")))
              (deny-response request (str "denied: " (:status result))))
            (throw ex-info "TokenInfo URL not available" {:http-code 503})))
        (deny-response request "no token given")))))
