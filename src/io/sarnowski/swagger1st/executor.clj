(ns io.sarnowski.swagger1st.executor
  (:require [clojure.tools.logging :as log]))

(defn map-function-name
  "Simple resolver function that resolves the operationId as a function name (including namespace)."
  [operationId]
  (resolve (symbol operationId)))

(defn- resolve-function
  "Resolves the target function with multiple resolver functions. First in list will 'win'."
  [operationId [first-fn & fallback-fns]]
  (if-let [function (first-fn operationId)]
    function
    (if fallback-fns
      (recur operationId fallback-fns)
      nil)))

(defn execute
  "A swagger middleware that uses a swagger definition for executing the given function."
  [context next-handler request {:keys [mappers]}]
  (if-let [swagger-request (:swagger-request request)]
    (if-let [operationId (get swagger-request "operationId")]
      (do
        (log/trace "matched swagger defined route for operation" operationId)

        ; found the definition, find mapping
        (if-let [call-fn (resolve-function operationId mappers)]
          (do
            (log/trace "found mapping for operation" operationId "to" call-fn)
            (call-fn request))

          (do
            (log/error "could not resolve handler for request" operationId)
            (throw (ex-info "Operation not implemented." {:http-code 501}))))))

    ; definition not found, skip for our middleware
    (throw (ex-info "Operation not defined." {:http-code 400}))))
