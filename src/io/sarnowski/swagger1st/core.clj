(ns io.sarnowski.swagger1st.core
  (:require [io.sarnowski.swagger1st.context :as context]
            [io.sarnowski.swagger1st.mapper :as mapper]
            [io.sarnowski.swagger1st.discoverer :as discoverer]
            [io.sarnowski.swagger1st.parser :as parser]
            [io.sarnowski.swagger1st.protector :as protector]
            [io.sarnowski.swagger1st.executor :as executor]
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
              :or   {on-init    (fn [context] context)
                     on-request (fn [context next-handler request] (next-handler request))}}]
  (let [context (on-init context)
        chain-handler (fn [next-handler]
                        (fn [request]
                          (let [request (assoc-in request [:swagger :context] context)]
                            (on-request context next-handler request))))]
    (update-in context [:chain-handlers] conj chain-handler)))

(defn discoverer
  "A swagger middleware that exposes the swagger definition and the swagger UI for public use."
  [context & {:keys [discovery-path definition-path ui-path overwrite-host?]
              :or   {discovery-path  "/.well-known/schema-discovery"
                     definition-path "/swagger.json"
                     ui-path         "/ui"
                     overwrite-host? true}}]
  (chain-handler
    context
    :on-request (partial discoverer/discover {:discovery-path  discovery-path
                                              :definition-path definition-path
                                              :ui-path         ui-path
                                              :overwrite-host? overwrite-host?})))

(defn mapper
  "A swagger middleware that uses a swagger definition for mapping a request to the specification."
  [context]
  (chain-handler
    context
    :on-init mapper/setup
    :on-request mapper/correlate))

(defn parser
  "A swagger middleware that uses a swagger definition for parsing parameters and crafting responses."
  [context & {:keys [] :as parser-options}]
  (chain-handler
    context
    :on-init #(parser/setup % parser-options)
    :on-request parser/parse))

(defn protector
  "A swagger middleware that uses a swagger definition for enforcing security constraints."
  [context security-handler]
  (chain-handler
    context
    :on-request (fn [context next-handler request]
                  (protector/protect context next-handler request security-handler))))

(defn executor
  "A swagger middleware that uses a swagger definition for executing the given function."
  [context & {:keys [resolver]
              :or   {resolver executor/operationId-to-function}}]
  (let [context (chain-handler
                  context
                  :on-init (partial executor/find-functions resolver)
                  :on-request executor/execute)]

    ; now crunch the context together to an executable function chain
    (reduce
      (fn [result next-handler]
        (next-handler result))
      (fn [context] (fn [next-handler] (fn [request] (throw (ex-info "no executor catched this, swagger1st misconfigured" nil)))))
      (:chain-handlers context))))
