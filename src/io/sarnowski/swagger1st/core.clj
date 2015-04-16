(ns io.sarnowski.swagger1st.core
  (:require [clojure.walk :as walk]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [io.sarnowski.swagger1st.schemas.swagger-2-0 :as swagger-2-0]
            [schema.core :as schema]
            [schema.utils]
            [ring.util.response :as r])
  (:import (clojure.lang ExceptionInfo)
           (java.text DateFormat)))

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

;(-> (swagger-context)
;    (swagger-mapper)
;    (swagger-security)
;    (swagger-executor))

(defn swagger-context
  "Loads a swagger definition and produces a context which will be used by the swagger middlewares to initialize."
  [swagger-definition-type swagger-definition]
  (let [definition (schema/validate swagger-2-0/root-object
                                    (load-swagger-definition swagger-definition-type swagger-definition))]
    {:definition     definition
     :chain-handlers (list)}))

(defn- get-definition
  "Resolves a $ref reference to its content."
  [r d]
  (let [path (rest (string/split r #"/"))]
    (get-in d path)))

(defn- denormalize-refs
  "Searches for $ref objects and replaces those with their target."
  [definition]
  (let [check-ref (fn [element]
                    (if-let [r (get element "$ref")]
                      (get-definition r definition)
                      element))]
    (walk/postwalk (fn [element]
                     (if (map? element)
                       (check-ref element)
                       element))
                   definition)))

(defn- inherit-map
  "Merges a map from parent to definition, overwriting keys with definition."
  [definition parent-definition map-name]
  (let [m (get definition map-name)
        pm (get parent-definition map-name)
        merged (merge pm m)]
    (assoc definition map-name merged)))

(defn- inherit-list
  "Denormalizes a collection, using the parent or replacing it with definition."
  [definition parent-definition col-name]
  (assoc definition col-name
                    (if-let [col (get definition col-name)]
                      col
                      (get parent-definition col-name))))

(defn- conj-if-not
  "Conjoins x to col if test-fn doesn't find an existing entry in col."
  [test-fn col x & xs]
  (let [col (if (empty? (filter (fn [y] (test-fn x y)) col))
              (conj col x)
              col)]
    (if xs
      (recur test-fn col (first xs) (next xs))
      col)))

(defn- inherit-list-elements
  "Denormalizes a collection, replacing entries that are equal."
  [definition parent-definition col-name if-not-fn]
  (assoc definition col-name
                    (let [pd (get parent-definition col-name)
                          d (get definition col-name)]
                      (remove nil?
                              (conj-if-not if-not-fn d (first pd) (next pd))))))

(defn- keys-equal?
  "Compares two maps if both have the same given keys."
  [x y ks]
  (every? (fn [k] (= (get x k) (get y k))) ks))

(defn- inherit-mimetypes
  "Inherit 'consumes' and 'produces' mimetypes if not defined."
  [definition parent-definition]
  (-> definition
      (inherit-list parent-definition "consumes")
      (inherit-list parent-definition "produces")
      (inherit-list parent-definition "security")))

(defn- inherit-path-spec
  "Denormalizes inheritance of parameters etc. from the path to operation."
  [definition parent-definition]
  (-> definition
      (inherit-mimetypes parent-definition)
      (inherit-list-elements parent-definition "parameters" (fn [x y] (keys-equal? x y ["name" "in"])))
      (inherit-map parent-definition "responses")
      (inherit-list parent-definition "security")))

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
   (inherit-path-spec
     operation-definition
     path-definition)])

(defn- extract-requests
  "Extracts request-key->operation-definition from a swagger definition."
  [definition]
  (let [inheriting-key? #{"parameters" "consumes" "produces" "schemes" "security"}]
    (->>
      ; create request-key / swagger-request tuples
      (for [[path path-definition] (get definition "paths")]
        (when-not (inheriting-key? path)
          (let [path-definition (inherit-mimetypes path-definition definition)]
            (for [[operation operation-definition] path-definition]
              (when-not (inheriting-key? operation)
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
  [swagger-requests]
  (fn [request]
    (->> swagger-requests
         (filter #(request-matches? % request))
         ; if we have multiple matches then its not well defined, just choose the first
         first)))

(defn- serialize-response
  "Serializes the response body according to the Content-Type."
  [request response]
  (let [supported-content-types {"application/json" json/write-str}]
    (if-let [serializer (supported-content-types (get-in response [:headers "Content-Type"]))]
      ; TODO check for allowed "produces" mimetypes and do object validation
      (assoc response :body (serializer (:body response)))
      response)))

(defn- add-cors-headers [response cors-origin]
  (-> response
      (r/header "Access-Control-Allow-Origin" cors-origin)
      (r/header "Access-Control-Max-Age" "3600")
      (r/header "Access-Control-Allow-Methods" "GET, POST, DELETE, PUT, PATCH, OPTIONS")
      (r/header "Access-Control-Allow-Headers" "*")))

(defn swagger-mapper
  "A swagger middleware that uses a swagger definition for mapping a request to the specification.
   Hint: if you set cors-origin, all OPTIONS requests will be ignored and used solely for CORS."
  [{:keys [definition] :as context} & {:keys [surpress-favicon cors-origin]
                                       :or   {surpress-favicon true}}]
  (let [swagger-requests (create-swagger-requests definition)
        lookup-swagger-request (create-swagger-request-lookup swagger-requests)]
    (log/debug "swagger-definition" definition)

    (let [chain-handler
          (fn [next-handler]
            (fn [request]
              (cond
                (and surpress-favicon (= "/favicon.ico" (:uri request)))
                (-> (r/response "No favicon available.")
                    (r/status 404))

                (and cors-origin (= :options (:request-method request)))
                (-> (r/response nil)
                    (r/status 200)
                    (add-cors-headers cors-origin))

                :else
                (let [[request-key swagger-request] (lookup-swagger-request request)]
                  (log/debug "swagger-request:" swagger-request)
                  (let [response (next-handler (-> request
                                                   (assoc :swagger-request swagger-request)
                                                   (assoc :swagger-request-key request-key)))]
                    (let [response (serialize-response request response)]
                      (if cors-origin
                        (add-cors-headers response cors-origin)
                        response)))))))]
      (-> context
          (update-in [:chain-handlers] conj chain-handler)
          (assoc :requests swagger-requests)))))

(defn- swaggerui-template
  "Loads the swagger ui template (index.html) and replaces certain keywords."
  [{definition :definition} definition-url]
  (let [template (slurp (io/resource "io/sarnowski/swagger1st/swaggerui/index.html"))
        vars {"$TITLE$"      (get-in definition ["info" "title"])
              "$DEFINITION$" definition-url}]
    (reduce (fn [template [var val]] (string/replace template var val)) template vars)))

(defn swagger-discovery
  "A swagger middleware that exposes the swagger definition and the swagger UI for public use."
  [{:keys [definition] :as context} & {:keys [discovery-path definition-path ui-path overwrite-host?]
                                       :or   {discovery-path  "/.well-known/schema-discovery"
                                              definition-path "/swagger.json"
                                              ui-path         "/ui/"
                                              overwrite-host? true}}]
  (let [chain-handler
        (fn [next-handler]
          (fn [request]
            (if (:swagger-request request)
              (next-handler request)
              (let [path (:uri request)]
                (cond
                  (= discovery-path path) (-> (r/response {:schema_url  definition-path
                                                           :schema_type "swagger-2.0"
                                                           :ui_url      ui-path})
                                              (r/header "Content-Type" "application/json"))
                  (= definition-path path) (-> (r/response (-> definition
                                                               (assoc "host" (or
                                                                               (-> request :headers (get "host"))
                                                                               (-> request :server-name)))))
                                               (r/header "Content-Type" "application/json"))
                  (= ui-path path) (-> (r/response (swaggerui-template context definition-path))
                                       (r/header "Content-Type" "text/html"))
                  (.startsWith path ui-path) (let [path (.substring path (count ui-path))]
                                               (-> (r/response (io/input-stream (io/resource (str "io/sarnowski/swagger1st/swaggerui/" path))))))
                  :else (next-handler request))))))]
    (update-in context [:chain-handlers] conj chain-handler)))

(defn- infer-item-value
  "'Casts' a value based on the definition to the concrete form (e.g. string -> integer)."
  [value definition]
  (if (nil? value)
    value
    (let [transformers {"integer"  #(Integer/parseInt %)
                        "long"     #(Long/parseLong %)
                        "float"    #(Float/parseFloat %)
                        "double"   #(Double/parseDouble %)
                        "string"   #(String. %)
                        "byte"     #(String. %)
                        "boolean"  #(Boolean/parseBoolean %)
                        "date"     #(.parse (DateFormat/getTimeInstance) %)
                        "dateTime" #(.parse (DateFormat/getTimeInstance) %)}]
      (if-let [type (get definition "type")]
        (if-let [transformer (get transformers type)]
          (try
            (transformer value)
            (catch Exception e
              (throw (ex-info (str "Argument " (get definition "name") " in " (get definition "in") " is of wrong type.") {:http-code 400}))))
          (throw (ex-info "type not supported for type inference" {:http-code 500})))
        value))))

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
    (-> (get keys (keyword (get definition "name")))
        (infer-item-value definition))))

(defn- extract-parameter-query
  "Extract a parameter from the request url."
  [{query-params :query-params} definition]
  (-> (get query-params (get definition "name"))
      (infer-item-value definition)))

(defn- extract-parameter-header
  "Extract a parameter from the request headers."
  [{headers :headers} definition]
  (-> (get headers (get definition "name"))
      (infer-item-value definition)))

(defn- extract-parameter-form
  "Extract a parameter from the request body form."
  [{form-params :form-params} definition]
  (-> (get form-params (get definition "name"))
      (infer-item-value definition)))

(defn- extract-parameter-body
  "Extract a parameter from the request body."
  [request parameter-definition]
  (let [request-definition (:swagger-request request)
        ; TODO honor charset= definitions of content-type header
        content-type (first (string/split (or
                                            (get (:headers request) "content-type")
                                            "application/octet-stream")
                                          #";"))
        allowed-content-types (into #{} (get request-definition "consumes"))
        ; TODO make this configurable
        supported-content-types {"application/json" (fn [body] (json/read-json (slurp body)))}]
    (if (allowed-content-types content-type)                ; TODO could be checked on initialization of ring handler chain
      (if-let [deserialize-fn (get supported-content-types content-type)]
        (try
          (deserialize-fn (:body request))
          (catch Exception e
            (throw (ex-info "Malformed body." {:http-code    400
                                               :content-type content-type}))))
        (:body request))
      (throw (ex-info "Content type not allowed."
                      {:http-code             406
                       :content-type          content-type
                       :allowed-content-types allowed-content-types})))))

(defn- extract-parameter
  "Extracts a parameter from the request according to the definition."
  [request definition]
  (let [in (keyword (get definition "in"))
        extractors {:path   extract-parameter-path
                    :query  extract-parameter-query
                    :header extract-parameter-header
                    :form   extract-parameter-form
                    :body   extract-parameter-body}
        extractor (get extractors in)
        parameter [in (keyword (get definition "name")) (extractor request definition)]]
    (log/trace "parameter:" parameter)
    parameter))

(defn swagger-parser
  "A swagger middleware that uses a swagger definition for parsing parameters and crafting responses."
  [context]
  (let [chain-handler
        (fn [next-handler]
          (fn [request]
            (if-let [swagger-request (:swagger-request request)]
              (let [parameter-groups (group-by first
                                               (map (fn [definition]
                                                      (extract-parameter request definition))
                                                    (get swagger-request "parameters")))
                    parameter-groups (into {}
                                           (map (fn [[group parameters]]
                                                  [group (into {} (map (fn [[_ k v]] [k v]) parameters))])
                                                parameter-groups))]
                (next-handler (assoc request :parameters parameter-groups)))
              (next-handler request))))]
    (update-in context [:chain-handlers] conj chain-handler)))

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

(defn swagger-validator
  "A swagger middleware that uses a swagger definition for validating incoming requests"
  [context]
  (let [request-validators (into {} (map (fn [[k v]] [k (parse-request-validator v)]) (:requests context)))
        chain-handler
        (fn [next-handler]
          (fn [request]
            (log/debug "Validators:" request-validators)
            (if-let [request-validator (get request-validators (:swagger-request-key request))]
              (do
                (validate-request request request-validator)
                (next-handler request))
              (do
                (log/trace "no validators found for request" (:swagger-request-key request) "; maybe no parameters at all?")
                (next-handler request)))))]
    (update-in context [:chain-handlers] conj chain-handler)))

(defn- check-security
  "Finds and executes a handler for a security definition."
  [context request name requirements handlers]
  (if-let [definition (get-in context [:definition "securityDefinitions" name])]
    (if-let [handler (get handlers name)]
      (handler request definition requirements)
      (throw (ex-info "securityHandler not defined" {:http-code 501})))
    (throw (ex-info "securityDefinition not defined" {:http-code 500}))))

(defn- enforce-security
  "Tries all security definitions of a request, if one accepts it."
  [chain-handler context request security handlers]
  (log/debug "Enforcing security checks for" (get-in request [:swagger-request "operationId"]))
  (let [all-results (map (fn [def]
                           (let [[name requirements] (first def)]
                             (check-security context request name requirements handlers)))
                         security)]
    ; if handler returned a request, everything is fine, else interpret it as response
    (if-let [request (some (fn [result]
                             (if (:swagger-request result) result nil))
                           all-results)]
      (do
        (log/debug "Security check OK for" (get-in request [:swagger-request "operationId"]))
        (chain-handler request))
      ; take the error response of the first security def
      (let [response (first all-results)]
        (log/debug "Security check FAILED for" (get-in request [:swagger-request "operationId"])
                   "; security definition:" security
                   "; response:" response
                   "; all results:" all-results)
        response))))

(defn swagger-security
  "A swagger middleware that uses a swagger definition for enforcing security constraints."
  [context security-handler]
  ; TODO prepare security lookups so that runtime lookup is faster
  (let [chain-handler
        (fn [next-handler]
          (fn [request]
            (if-let [security (get-in request [:swagger-request "security"])]
              (enforce-security next-handler context request security security-handler)
              (next-handler request))))]
    (update-in context [:chain-handlers] conj chain-handler)))

(defn swagger-ring
  "Allows easy integration of standard ring middleware into the swagger1st middleware's."
  [context middleware-fn & args]
  (let [chain-handler
        (fn [next-handler]
          (apply middleware-fn next-handler args))]
    (update-in context [:chain-handlers] conj chain-handler)))

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
  "A swagger middleware that uses a swagger definition for executing the given function."
  [context & {:keys [mappers]
              :or   {mappers [map-function-name]}}]
  (let [handler
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
                    (throw (ex-info "Operation not implemented." {:http-code 501}))))))

            ; definition not found, skip for our middleware
            (throw (ex-info "Operation not defined." {:http-code 400}))))]
    ; build chain from chain-handlers list
    (reduce
      (fn [result next-handler]
        (next-handler result))
      handler
      (:chain-handlers context))))
