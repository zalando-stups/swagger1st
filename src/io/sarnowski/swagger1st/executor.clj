(ns io.sarnowski.swagger1st.executor
  (:require [clojure.tools.logging :as log]
            [io.sarnowski.swagger1st.util.api :as api]))

(defn function-by-name
  "Simple resolver function that resolves the operationId as a function name (including namespace)."
  [f]
  (let [fn-sym (symbol f)
        fn-ns (symbol (namespace fn-sym))
        fn-name (symbol (name fn-sym))]
    (require [fn-ns])
    (ns-resolve fn-ns fn-name)))

(defn operationId-to-function
  "Resolves the target function with multiple resolver functions. First in list will 'win'."
  [request-definition]
  (let [operationId (get request-definition "operationId")]
    (function-by-name operationId)))

(defn find-functions
  "Finds all functions that get executed on request."
  [resolver context]
  (let [executors (into {} (map (fn [[k v]]
                                  (if-let [operation (resolver v)]
                                    [k operation]
                                    (api/throw-error 500 "no operation found" v)))
                                (:requests context)))]
    (log/debug "executors:" executors)
    (assoc context :executors executors)))

(defn execute
  "A swagger middleware that uses a swagger definition for executing the given function."
  [context _ request]
  (let [key (-> request :swagger :key)
        operation (get (-> context :executors) key)]
    (log/debug "executing" key "->" operation)
    (operation request)))
