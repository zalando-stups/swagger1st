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
  (let [parameters        (get definition "parameters")
        parent-parameters (get parent-definition "parameters")
        merged-parameters (merge parent-parameters parameters)]
    (assoc definition "parameters" merged-parameters)))

(defn- split-path
  "Splits a / separated path into its segments and replaces all variable entries (e.g. {name}) with nil."
  [path]
  (let [split (fn [^String s] (.split s "/"))]
    (-> path split rest)))

(defn- nil-variables
  "Replace variables in a path segment collection with nil."
  [seg]
  (if (re-matches #"\{.*\}" seg) nil seg))

(defn- extract-requests
  "Extracts request-key->operation-definition from a swagger definition."
  [definition]
  (into {}
        (remove nil?
                (apply concat
                       (for [[path path-definition] (get definition "paths")]
                         (when-not (contains? #{"parameters"} path) ; maybe (if ... [] (let ...))
                           (let [path-definition (denormalize-inheritance path-definition definition)]
                             (for [[operation operation-definition] path-definition]
                               (when-not (contains? #{"parameters"} path)
                                 [; request-key
                                  {:operation operation
                                   :path      (->> path
                                                   split-path
                                                   (map nil-variables))}
                                  ; swagger-request
                                  (denormalize-inheritance
                                    operation-definition
                                    path-definition)])))))))))

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
    (let [pairs         (map #(vector %1 %2) path-template path-real)
          pair-matches? (fn [[t r]] (or (nil? t) (= t r)))]
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
           first                                            ; if we have multiple matches then its not well defined, just choose the first
           second                                           ; first is request-key, second is swagger-definition in the resulting tuple
           ))))

;;; The middlewares

(defn swagger-mapper
  "A ring middleware that uses a swagger definition for mapping a request to the specification."
  [chain-handler swagger-definition-type swagger-definition]
  (let [definition             (schema/validate s/swagger-schema
                                                (load-swagger-definition swagger-definition-type swagger-definition))
        lookup-swagger-request (create-swagger-request-lookup definition)]
    (log/debug "swagger-definition" definition)

    (fn [request]
      (let [swagger-request (lookup-swagger-request request)]
        (log/debug "swagger-request:" swagger-request)
        (chain-handler (-> request
                           (assoc :swagger definition)
                           (assoc :swagger-request swagger-request)))))))

(defn swagger-parser
  "A ring middleware that uses a swagger definition for parsing parameters and crafting responses."
  ; TODO optional map of (de)serializer functions for mimetypes
  [chain-handler]
  (fn [request]
    ; TODO noop currently, parse parameters and (de)serialize them according to mimetypes
    (chain-handler request)))

(defn swagger-validator
  "A ring middleware that uses a swagger definition for validating incoming requests and their responses."
  [chain-handler]
  (fn [request]
    ; TODO noop currently, validate request (and response?!) from swagger def
    (chain-handler request)))

(defn swagger-executor
  "A ring middleware that uses a swagger definition for executing the given function."
  [& {:keys [mappings auto-map-fn?]
      :or   {mappings     {}
             ; TODO remove auto-map-fn? and call 'mappings' as a function. map still works but can have a default of
             ; 'resolve-function' mapper - also provide possibility to chain multiple functions as fallback strategies
             ; like :mapper [{} resolve-function] or as default :mapper [resolve-function].
             auto-map-fn? true}}]
  (fn [request]
    (if-let [swagger-request (:swagger-request request)]
      (if-let [operationId (get swagger-request "operationId")]
        (do
          (log/trace "matched swagger defined route for operation" operationId)

          ; found the definition, find mapping
          (if-let [call-fn (get mappings operationId)]
            (do
              (log/trace "found mapping for operation" operationId "to" call-fn)
              (call-fn request))

            ; no mapping, auto map to fn? see TODO for auto-map-fn?
            (if auto-map-fn?
              (let [call-fn (resolve (symbol operationId))]
                (log/trace "auto mapped operation" operationId "to" call-fn)
                (call-fn request))

              ; no auto mapping enabled
              (do
                (log/error "could not resolve handler for request" operationId)
                {:status  501
                 :headers {"Content-Type" "plain/text"}
                 :body    "Operation not implemented."})))))

      ; definition not found, skip for our middleware
      {:status  400
       :headers {"Content-Type" "plain/text"}
       :body    "Operation not defined."})))
