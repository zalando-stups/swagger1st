(ns io.sarnowski.swagger1st.validator
  (:require [clojure.tools.logging :as log]
            [schema.core :as schema]
            [io.sarnowski.swagger1st.schemas.swagger-2-0 :as swagger-2-0])
  (:import (clojure.lang ExceptionInfo)))

(defn parse-item-validator
  "Validates one item definition as specified by swagger."
  [item]
  (if-let [type (get item "type")]
    (if-let [primitive-validator (get swagger-2-0/primitives type)]
      (if (get item "required" true)
        primitive-validator
        (schema/maybe primitive-validator))

      ;(throw (ex-info (str "Invalid schema definition type " type) {:http-code 500}))) TODO instead of Any
      schema/Any)
    schema/Any))

(defn- parse-request-validator
  "Creates a validator map with a compound key of {:name :in} and a prismatic schema validation data structure."
  [request]
  (map (fn [parameter]
         [(keyword (get parameter "in"))
          (keyword (get parameter "name"))
          (parse-item-validator parameter)])
       (get request "parameters")))

(defn create-validators
  "Creates a map of request key to validator."
  [context]
  (let [validators (into {} (map (fn [[k v]]
                                   [k (parse-request-validator v)])
                                 (:requests context)))]
    (log/debug "validators:" validators)
    (assoc context :validators validators)))

(defn- validate-request
  "Validates all request validators against an actual request."
  [request request-validator]
  (let [errors (atom [])]
    (doseq [[in name validator] request-validator]
      (let [value (get-in request [:parameters in name])]
        (try
          (schema/validate validator value)
          (catch ExceptionInfo e
            (swap! errors conj {:name name :in in :detail (ex-data e)})))))
    (when-not (empty? @errors)
      (let [payload {:http-code         412
                     :invalid-arguments (map (fn [error]
                                               (assoc error :detail (-> error :detail :error schema.utils/validation-error-explain str)))
                                             @errors)}]
        (log/trace "rejecting request because of invalid arguments:" payload)
        (throw (ex-info "Invalid arguments." payload))))))

(defn validate
  "A swagger middleware that uses a swagger definition for validating incoming requests"
  [{:keys [validators]} next-handler request]
  (let [key (-> request :swagger :key)]
    (if-let [request-validator (get validators key)]
      (do
        (validate-request request request-validator)
        (next-handler request))
      (do
        (log/trace "no validators found for request" key "; maybe no parameters at all?")
        (next-handler request)))))
