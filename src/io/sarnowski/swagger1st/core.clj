(ns io.sarnowski.swagger1st.core
  (:require [clojure.walk :as walk]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [io.sarnowski.swagger1st.schema :as s]
            [schema.core :as schema]))

;;; Load definitions

(defmulti load-swagger-definition (fn [definition-type _] definition-type))

(defmethod load-swagger-definition ::direct [_ definition]
  definition)

(defmethod load-swagger-definition ::json [_ definition]
  (json/read-str definition))

(defmethod load-swagger-definition ::json-file [_ definition]
  (load-swagger-definition ::json (slurp definition)))

(defmethod load-swagger-definition ::json-cp [_ definition]
  (load-swagger-definition ::json-file (io/resource definition)))

(defmethod load-swagger-definition ::yaml [_ definition]
  (yaml/parse-string definition :keywords false))

(defmethod load-swagger-definition ::yaml-file [_ definition]
  (load-swagger-definition ::yaml (slurp definition)))

(defmethod load-swagger-definition ::yaml-cp [_ definition]
  (load-swagger-definition ::yaml-file (io/resource definition)))

;;; Runtime dispatching functions

(defn- get-definition
  "Resolves a $ref reference to its content."
  [r d]
  (let [path (rest (string/split r #"/"))]
    (get-in d path)))

(defn- denormalize-ref
  "If a $ref is detected, the referenced content is returned, else the original content."
  [definition data]
  (if-let [r (get data "$ref")]
    (get-definition r definition)
    data))

(defn- denormalize-refs
  "Searches for $ref objects and replaces those with their target."
  [definition]
  (let [f (fn [[k v]] [k (denormalize-ref definition v)])]
    (walk/prewalk (fn [x] (if (map? x) (into {} (map f x)) x)) definition)))

(defn- denormalize-inheritance
  "Denormalizes inheritance of parameters etc."
  [definition parent-definition]
  ; TODO security, consumes, produces, ...
  ; parameters
  (let [parameters (get definition "parameters")
        parent-parameters (get parent-definition "parameters")
        merged-parameters (merge parent-parameters parameters)]
    (assoc definition "parameters" merged-parameters)))

(defn- split-path
  "Splits a / separated path into its segments and replaces all variable entries (e.g. {name}) with nil."
  [path]
  (let [split (fn [^String s] (.split s "/"))]
    (-> path split rest)))

(defn- variable-to-keyword
  "Replaces a variable path segment (like /{username}/) with the variable name as keyword (like :username)."
  [seg]
  (if-let [variable-name (second (re-matches #"\{(.*)\}" seg))]
    ; use keywords for variable names
    (keyword variable-name)
    ; no variable found, return original segment
    seg))

(defn- create-request-tuple
  "Generates easier to digest request tuples from operations and paths."
  [operation operation-definition path path-definition]
  [; request-key
   {:operation operation
    :path      (->> path
                    split-path
                    (map variable-to-keyword))}
   ; swagger-request
   (denormalize-inheritance
     operation-definition
     path-definition)])

(defn- extract-requests
  "Extracts request-key->operation-definition from a swagger definition."
  [definition]
  (let [non-path-keys #{"parameters"}]
    (->>
      ; create request-key / swagger-request tuples
      (for [[path path-definition] (get definition "paths")]
        (when-not (contains? non-path-keys path)
          (let [path-definition (denormalize-inheritance path-definition definition)]
            (for [[operation operation-definition] path-definition]
              (when-not (contains? non-path-keys path)
                (create-request-tuple operation operation-definition path path-definition))))))
      ; streamline tuples and bring into a map
      (apply concat)
      (remove nil?)
      (into {}))))

(defn- create-swagger-requests
  "Creates a map of 'request-key' -> 'swagger-definition' entries. The request-key can be used to efficiently lookup
   requests. The swagger-definition contains denormalized information about the request specification (all refs and
   inheritance is denormalized)."
  [definition]
  (-> definition
      denormalize-refs
      extract-requests))

(defn- path-machtes?
  "Matches a template path with a real path. Paths are provided as collections of their segments. If the template has
   a nil value, it is a dynamic segment."
  [path-template path-real]
  (when (= (count path-template) (count path-real))
    (let [pairs (map #(vector %1 %2) path-template path-real)
          pair-matches? (fn [[t r]] (or (keyword? t) (= t r)))]
      (every? pair-matches? pairs))))

(defn- request-matches?
  "Checks if the given request matches a defined swagger-request."
  [[request-key _] request]
  (and (= (:operation request-key) (name (:request-method request)))
       (path-machtes? (:path request-key) (split-path (:uri request)))))

(defn- create-swagger-request-lookup
  "Creates a function that can do efficient lookups of requests."
  [definition]
  (let [swagger-requests (create-swagger-requests definition)]
    (fn [request]
      (->> swagger-requests
           (filter #(request-matches? % request))
           ; if we have multiple matches then its not well defined, just choose the first
           first))))

(defn swagger-mapper
  "A ring middleware that uses a swagger definition for mapping a request to the specification."
  [chain-handler swagger-definition-type swagger-definition]
  (let [definition (schema/validate s/swagger-schema
                                    (load-swagger-definition swagger-definition-type swagger-definition))
        lookup-swagger-request (create-swagger-request-lookup definition)]
    (log/debug "swagger-definition" definition)

    (fn [request]
      (let [[request-key swagger-request] (lookup-swagger-request request)]
        (log/debug "swagger-request:" swagger-request)
        (chain-handler (-> request
                           (assoc :swagger definition)
                           (assoc :swagger-request swagger-request)
                           (assoc :swagger-request-key request-key)))))))

(defn- extract-parameter-path
  "Extract a parameter from the request path."
  [request definition]
  (let [{template :path} (:swagger-request-key request)
        keys (map (fn [t r] (if (keyword? t) [t r] nil))
                  template
                  (split-path (:uri request)))
        keys (->> keys
                  (remove nil?)
                  (into {}))]
    (get keys (keyword (get definition "name")))))

(defn- extract-parameter-query
  "Extract a parameter from the request url."
  [{query-params :query-params} definition]
  (get query-params (get definition "name")))

(defn- extract-parameter-header
  "Extract a parameter from the request headers."
  [{headers :headers} definition]
  (get headers (get definition "name")))

(defn- extract-parameter-form
  "Extract a parameter from the request body form."
  [{form-params :form-params} definition]
  (get form-params (get definition "name")))

(defn- extract-parameter
  "Extracts a parameter from the request according to the definition."
  [request definition]
  (let [extractors {"path" extract-parameter-path
                    "query" extract-parameter-query
                    "header" extract-parameter-header
                    "form" extract-parameter-form}
        extractor (get extractors (get definition "in"))
        parameter [(keyword (get definition "name")) (extractor request definition)]]
    (log/trace "parameter:" parameter)
    parameter))

(defn swagger-parser
  "A ring middleware that uses a swagger definition for parsing parameters and crafting responses."
  ; TODO optional map of (de)serializer functions for body mimetypes
  [chain-handler]
  (fn [request]
    (if-let [swagger-request (:swagger-request request)]
      (chain-handler
        (assoc request :parameters
                       (into {}
                             (map (fn [[_ definition]]
                                    (extract-parameter request definition))
                                  (get swagger-request "parameters")))))
      (chain-handler request))))

(defn swagger-validator
  "A ring middleware that uses a swagger definition for validating incoming requests and their responses."
  [chain-handler]
  (fn [request]
    ; TODO noop currently, validate request (and response?!) from swagger def
    (chain-handler request)))

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

(defn swagger-executor
  "A ring middleware that uses a swagger definition for executing the given function."
  [& {:keys [mappers]
      :or   {mappers [map-function-name]}}]
  (fn [request]
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
              {:status  501
               :headers {"Content-Type" "plain/text"}
               :body    "Operation not implemented."}))))

      ; definition not found, skip for our middleware
      {:status  400
       :headers {"Content-Type" "plain/text"}
       :body    "Operation not defined."})))
