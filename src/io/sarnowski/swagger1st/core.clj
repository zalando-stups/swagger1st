(ns io.sarnowski.swagger1st.core
  (:require [io.sarnowski.swagger1st.context :as context]
            [io.sarnowski.swagger1st.mapper :as mapper]
            [io.sarnowski.swagger1st.discoverer :as discovery]
            [io.sarnowski.swagger1st.parser :as parser]
            [io.sarnowski.swagger1st.validator :as validator]
            [io.sarnowski.swagger1st.protector :as protector]
            [io.sarnowski.swagger1st.executor :as executor]
            [io.sarnowski.swagger1st.util.api :as api]
            [clojure.tools.logging :as log]
            [ring.util.response :as r]))

(defn context
  "Loads a swagger definition and produces a context which will be used by the swagger middlewares to initialize."
  [definition-type definition-source & {:keys [validate?]
                                        :or   {validate? true}}]
  (context/create-context definition-type definition-source validate?))

(defn ring
  "Allows easy integration of standard ring middleware into the swagger1st chain."
  [context middleware-fn & args]
  (let [chain-handler
        (fn [next-handler]
          (apply middleware-fn next-handler args))]
    (update-in context [:chain-handlers] conj chain-handler)))

(defn chain-handler
  "Helper to create handler chains."
  [context & {:keys [on-init on-request]
              :or {on-init (fn [context] context)
                   on-update (fn [context next-handler request] (next-handler context))}}]
  (let [context (on-init context)
        chain-handler  (fn [next-handler]
                         (fn [request]
                           (let [request (assoc-in request [:swagger :context] context)]
                             (on-request context next-handler request))))]
    (update-in context [:chain-handlers] conj chain-handler)))

(defn discoverer
  "A swagger middleware that exposes the swagger definition and the swagger UI for public use."
  [context & {:keys [discovery-path definition-path ui-path overwrite-host?]
              :or   {discovery-path  "/.well-known/schema-discovery"
                     definition-path "/swagger.json"
                     ui-path         "/ui/"
                     overwrite-host? true}}]
  (chain-handler
    context
    :on-request
    (fn [context next-handler request]
      (if-let [response (discovery/process request (:definition context) {:discovery-path discovery-path
                                                                          :definition-path definition-path
                                                                          :ui-path ui-path
                                                                          :overwrite-host? overwrite-host?})]
        response
        (next-handler request)))))

(defn mapper
  "A swagger middleware that uses a swagger definition for mapping a request to the specification."
  [context]
  (chain-handler
    context
    :on-init
      (fn [{:keys [definition] :as context}]
        (log/debug "swagger-definition:" definition)
        (assoc context :requests (mapper/create-requests definition)))
    :on-request
      (fn [{:keys [requests]} next-handler request]
        (let [[key swagger-request] (mapper/lookup-request requests request)]
          (if (nil? swagger-request)
            (api/error 404 (str (.toUpperCase (-> request :request-method name)) " " (-> request :uri) " not found."))
            (do
              (log/debug "swagger-request ("  key "):" swagger-request)
          (let [request (-> request
                            (assoc-in [:swagger :request] swagger-request)
                            (assoc-in [:swagger :key] key))
                response (next-handler request)]
            (mapper/serialize-response request response))))))))

(defn parser
  "A swagger middleware that uses a swagger definition for parsing parameters and crafting responses."
  [context]
  (chain-handler
    context
    :on-request parser/parse))

(defn validator
  "A swagger middleware that uses a swagger definition for validating incoming requests"
  [context]
  (chain-handler
    context
    :on-init validator/create-validators
    :on-request validator/validate))

(defn protector
  "A swagger middleware that uses a swagger definition for enforcing security constraints."
  [context security-handler]
  (chain-handler
    context
    :on-request (fn [context next-handler request]
                  (protector/protect context next-handler request security-handler))))

(defn executor
  "A swagger middleware that uses a swagger definition for executing the given function."
  [context & {:keys [mappers]
              :or   {mappers [executor/map-function-name]}
              :as config}]
  (let [context (chain-handler
    context
    :on-request
    (fn [context next-handler request]
                  (executor/execute context next-handler request config)))]
    (reduce
      (fn [result next-handler]
        (next-handler result))
      (fn [context] (fn [next-handler] (fn [request] (throw (ex-info "no executor catched this" nil)))))
      (:chain-handlers context))))