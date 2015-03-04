(ns io.sarnowski.swagger1st.core
  (:require [clojure.walk :as walk]
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

(defn- get-definition [r d]
  (let [path (rest (split-with #"/" r))]
    (get-in d path)))

(defn- denormalize-ref [definition data]
  (if-let [r (get data "$ref")]
    (get-definition r definition)
    data))

(defn- denormalize-refs [definition]
  (let [f (fn [[k v]] [k (denormalize-ref definition v)])]
    (walk/prewalk (fn [x] (if (map? x) (into {} (map f x)) x)) definition)))

(defn- extract-requests [definition]
  (into {}
        (apply concat
               (for [[path path-definition] (get definition "paths")]
                 (for [[operation operation-definition] path-definition]
                   [{:operation operation
                     :path path}
                    operation-definition])))))

(defn- create-swagger-requests [definition]
  (-> definition
      denormalize-refs
      extract-requests))

(defn- request-matches? [[request-key swagger-request] request]
  (and (= (:operation request-key) (name (:request-method request)))
       (= (:path request-key) (:uri request))))

(defn- create-swagger-request-lookup [definition]
  (let [swagger-requests (create-swagger-requests definition)]
    (fn [request]
      (second (first (filter #(request-matches? % request) swagger-requests))))))

;;; The middleware

(defn swagger-mapper
  "A ring middleware that uses a swagger definition for mapping a request to the specification."
  [chain-handler swagger-definition-type swagger-definition]
  (let [definition (schema/validate s/swagger-schema
                                    (load-swagger-definition swagger-definition-type swagger-definition))
        lookup-swagger-request (create-swagger-request-lookup definition)]
    (log/debug "swagger-definition" definition)

    (fn [request]
      (let [swagger-request (lookup-swagger-request request)]
        (log/trace "swagger-request:" swagger-request)
        (chain-handler (-> request
                           (assoc :swagger definition)
                           (assoc :swagger-request swagger-request)))))))

(defn swagger-validator
  "A ring middleware that uses a swagger definition for validating incoming requests and their responses."
  [chain-handler]
  (fn [request]
    ; TODO noop currently, validate request (and response?!) from swagger def
    (chain-handler request)))

(defn swagger-executor
  "A ring middleware that uses a swagger definition for executing the given function."
  [& {:keys [mappings auto-map-fn?]
      :or {mappings {}
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

            ; no mapping, auto map to fn?
            (if auto-map-fn?
              (let [call-fn (resolve (symbol operationId))]
                (log/trace "auto mapped operation" operationId "to" call-fn)
                (call-fn request))

              ; no auto mapping enabled
              (do
                (log/error "could not resolve handler for request" operationId)
                {:status 500
                 :headers {"Content-Type" "plain/text"}
                 :body "Internal server error."})))))

      ; definition not found, skip for our middleware
      {:status 404
       :headers {"Content-Type" "plain/text"}
       :body "Not found."})))
