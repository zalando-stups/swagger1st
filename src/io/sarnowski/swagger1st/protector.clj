(ns io.sarnowski.swagger1st.protector
  (:require [clojure.tools.logging :as log]))

(defn- check-security
  "Finds and executes a handler for a security definition."
  [context request name requirements handlers]
  (if-let [definition (get-in context [:definition "securityDefinitions" name])]
    (if-let [handler (get handlers name)]
      (handler request definition requirements)
      (throw (ex-info (str "securityHandler " name " not defined") {:http-code 501})))
    (throw (ex-info "securityDefinition not defined" {:http-code 500}))))

(defn- enforce-security
  "Tries all security definitions of a request, if one accepts it."
  [chain-handler context request security handlers]
  (log/debug "Enforcing security checks for" (get-in request [:swagger :request "operationId"]))
  (let [all-results (map (fn [def]
                           (let [[name requirements] (first def)]
                             (check-security context request name requirements handlers)))
                         security)]
    ; if handler returned a request, everything is fine, else interpret it as response
    (if-let [request (some (fn [result]
                             (if (-> result :swagger :request) result nil))
                           all-results)]
      (do
        (log/debug "Security check OK for" (get-in request [:swagger :request "operationId"]))
        (chain-handler request))
      ; take the error response of the first security def
      (let [response (first all-results)]
        (log/debug "Security check FAILED for" (get-in request [:swagger :request "operationId"])
                   "; security definition:" security
                   "; response:" response
                   "; all results:" all-results)
        response))))

(defn protect
  "A swagger middleware that uses a swagger definition for enforcing security constraints."
  [context next-handler request security-handler]
  ; TODO prepare security lookups so that runtime lookup is faster
  (if-let [security (get-in request [:swagger :request "security"])]
    (enforce-security next-handler context request security security-handler)
    (next-handler request)))
