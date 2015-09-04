(ns io.sarnowski.swagger1st.parser
  (:require [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [io.sarnowski.swagger1st.mapper :refer [split-path]]
            [clj-time.format :as f]
            [io.sarnowski.swagger1st.util.api :as api])
  (:import (org.joda.time DateTime)
           (java.io PrintWriter)
           (clojure.lang ExceptionInfo)))

(defn- serialize-date-time
  "Serializes a org.joda.time.DateTime to JSON in a compliant way."
  [^DateTime date-time #^PrintWriter out]
  (.print out (-> (f/formatters :date-time)
                  (f/unparse date-time)
                  (json/write-str))))

; add json capability to org.joda.time.DateTime
(extend DateTime json/JSONWriter
  {:-write serialize-date-time})

(def json-content-type?
  ; TODO could be more precise but also complex
  ; examples:
  ;  application/json
  ;  application/vnd.order+json
  #"application/.*json")

(defn throw-value-error
  "Throws a validation error if value cannot be parsed or is invalid."
  [value definition path & reason]
  (throw (ex-info
           (str "Value " (json/write-str value :escape-slash false)
                " in " (string/join "->" path)
                (if (get definition "required")
                  " (required)"
                  " (not required)")
                " cannot be used as type '" (get definition "type") "'"
                (if (get definition "format")
                  (str " with format '" (get definition "format") "'")
                  "")
                " because " (string/join (map (fn [v] (if (keyword? v) (name v) v)) reason)) ".")
           {:http-code  400
            :value      value
            :path       path
            :definition definition})))

(defn extract-parameter-path
  "Extract a parameter from the request path."
  [request definition]
  (let [[_ template-path] (-> request :swagger :key)
        ; TODO split paths before once and only access parsed paths here
        parameters (map (fn [t r] (when (keyword? t) [t r]))
                        template-path
                        (split-path (:uri request)))
        parameters (->> parameters
                        (remove nil?)
                        (into {}))]
    (get parameters (keyword (get definition "name")))))

(defn extract-parameter-query
  "Extract a parameter from the request url."
  [{query-params :query-params} definition]
  (get query-params (get definition "name")))

(defn extract-parameter-header
  "Extract a parameter from the request headers."
  [{headers :headers} definition]
  (get headers (get definition "name")))

(defn extract-parameter-form
  "Extract a parameter from the request body form."
  [{form-params :form-params} definition]
  (get form-params (get definition "name")))

(defn extract-parameter-body
  "Extract a parameter from the request body."
  [request parameter-definition]
  (let [request-definition (-> request :swagger :request)
        ; TODO honor charset= definitions of content-type header
        [content-type charset] (string/split (or
                                               (get (:headers request) "content-type")
                                               "application/octet-stream")
                                             #";")
        content-type (string/trim content-type)
        allowed-content-types (set (get request-definition "consumes"))
        ; TODO make this configurable
        supported-content-types {json-content-type? (fn [body] (json/read-json (slurp body)))}]

    (if (allowed-content-types content-type)                ; TODO could be checked on initialization of ring handler chain
      (if-let [deserialize-fn (second (first (filter (fn [[pattern _]] (re-matches pattern content-type)) supported-content-types)))]
        (try
          (deserialize-fn (:body request))
          (catch Exception e
            (api/throw-error 400 (str "Body not parsable with given content type.")
                             {:content-type content-type
                              :error        (str e)})))
        ; if we cannot deserialize it, just forward it to the executing function
        (:body request))
      (api/throw-error 406 "Content type not allowed."
                       {:content-type          content-type
                        :allowed-content-types allowed-content-types}))))

(def extractors
  {"path"     extract-parameter-path
   "query"    extract-parameter-query
   "header"   extract-parameter-header
   "formData" extract-parameter-form
   "body"     extract-parameter-body})

(def date-time-formatter (f/formatters :date-time))
(def date-formatter (f/formatters :date))
(def string-transformers {; basic types
                          "string"    identity
                          "integer"   #(Long/parseLong %)
                          "number"    #(Float/parseFloat %)
                          "boolean"   #(Boolean/parseBoolean %)

                          ; special formats
                          ; TODO support pluggable formats for e.g. 'email' or 'uuid'
                          "int32"     #(Integer/parseInt %)
                          "int64"     #(Long/parseLong %)
                          "float"     #(Float/parseFloat %)
                          "double"    #(Double/parseDouble %)
                          "date"      #(f/parse date-formatter %)
                          "date-time" #(f/parse date-time-formatter %)})

(defn coerce-string [value definition path]
  (if (string? value)
    (let [err (partial throw-value-error value definition path)]
      (if-let [string-transformer (or (get string-transformers (get definition "format"))
                                      (get string-transformers (get definition "type")))]
        (try
          (string-transformer value)
          (catch Exception e
            (err "it cannot be transformed: " (.getMessage e))))
        ; TODO check on setup, not runtime
        (err "its format is not supported")))
    value))

(defmulti create-value-parser
          "Creates a parser function that takes a value and coerces and validates it."
          (fn [definition _ _]
            (get definition "type")))

(defmethod create-value-parser "object" [definition path parser-options]
  (let [required-keys (set (map keyword (get definition "required")))
        key-parsers (into {} (map (fn [[k v]]
                                    [(keyword k) (create-value-parser v (conj path k) parser-options)])
                                  (get definition "properties")))]
    (fn [value]
      (let [err (partial throw-value-error value definition path)]
        (if (map? value)
          (do
            ; check all required keys are present
            (let [provided-keys (set (keys value))]
              (doseq [required-key required-keys]
                (when-not (contains? provided-keys required-key)
                  (err "it misses the key '" (name required-key) "'"))))
            ; traverse into all keys
            (into {}
                  (map (fn [[k v]]
                         (if-let [parser (key-parsers k)]
                           [k (parser v)]
                           (if (:allow-undefined-keys parser-options)
                             [k v]
                             (err "the given attribute '" k "' is not defined"))))
                       value)))
          (err "it is not an object"))))))

(defmethod create-value-parser "array" [definition path parser-options]
  (let [items-definition (get definition "items")
        items-parser (create-value-parser items-definition path parser-options)]
    (fn [value]
      (let [err (partial throw-value-error value definition path)
            check-count (fn [value]
                          (if-let [max-items (get definition "maxItems")]
                            (when-not (<= (count value) max-items)
                              (err "it has more than '" max-items "' items")))
                          (if-let [min-items (get definition "minItems")]
                            (when-not (>= (count value) min-items)
                              (err "it has less than '" min-items "' items"))))
            check-unique (fn [value]
                           (if (get definition "uniqueItems")
                             (when-not (or (empty? value) (apply distinct? value))
                               (err "it has duplicate items"))))]
        (when-not (instance? Iterable value)
          (err "it is not an array"))
        (check-count value)
        (check-unique value)
        (map items-parser value)))))

(defmethod create-value-parser "string" [definition path parser-options]
  (let [check-pattern (if (contains? definition "pattern")
                        (let [pattern (re-pattern (get definition "pattern"))]
                          (fn [value]
                            (let [err (partial throw-value-error value definition path)]
                              (when-not (re-matches pattern value)
                                (err "it does not match the given pattern '" (get definition "pattern") "'")))))
                        ; noop
                        (fn [value] nil))
        check-size (fn [value]
                     (let [err (partial throw-value-error value definition path)]
                       (if-let [min-length (get definition "minLength")]
                         (when-not (>= (count value) min-length)
                           (err "it is shorter than '" min-length "' characters")))
                       (if-let [max-length (get definition "maxLength")]
                         (when-not (<= (count value) max-length)
                           (err "it is longer than '" max-length "' characters")))))]
    (fn [value]
      (let [err (partial throw-value-error value definition path)
            value (coerce-string value definition path)]
        (if (or (nil? value) (and (string? value) (empty? value)))
          (if (get definition "required")
            (err "it is required")
            value)
          (do
            (when (string? value)
              (check-pattern value)
              (check-size value))
            value))))))

(defn create-value-parser-number [definition path parser-options]
  (fn [value]
    (let [err (partial throw-value-error value definition path)
          value (coerce-string value definition path)
          check-multiple-of (fn [value]
                              (if-let [multiple-of (get definition "multipleOf")]
                                (when-not (zero? (mod value multiple-of))
                                  (err "it is not a multiple of '" multiple-of "'"))))
          check-range (fn [value]
                        (if-let [minimum (get definition "minimum")]
                          (if (get definition "exclusiveMinimum")
                            (when (<= value minimum)
                              (err "it is lower than or equal to '" minimum "'"))
                            (when (< value minimum)
                              (err "it is lower than '" minimum "'"))))
                        (if-let [maximum (get definition "maximum")]
                          (if (get definition "exclusiveMaximum")
                            (when (>= value maximum)
                              (err "it is higher than or equal to '" maximum "'"))
                            (when (> value maximum)
                              (err "it is higher than '" maximum "'")))))]
      (if (nil? value)
        (if (get definition "required")
          (err "it is required")
          value)
        (do
          (check-multiple-of value)
          (check-range value)
          value)))))

(defmethod create-value-parser "integer" [definition path parser-options]
  (create-value-parser-number definition path parser-options))

(defmethod create-value-parser "number" [definition path parser-options]
  (create-value-parser-number definition path parser-options))

(defmethod create-value-parser "boolean" [definition path parser-options]
  (fn [value]
    (let [err (partial throw-value-error value definition path)
          value (coerce-string value definition path)]
      (if (nil? value)
        (if (get definition "required")
          (err "it is required")
          value)
        value))))

(defmethod create-value-parser :default [definition path parser-options]
  ; ignore this value and just pass through
  (fn [value]
    value))

(defn create-parser
  "Creates a parsing function for the given parameter definition."
  [parameter-definition parser-options]
  (let [pin (get parameter-definition "in")
        pname (get parameter-definition "name")

        parameter-definition (if (= "body" pin) (get parameter-definition "schema") parameter-definition)

        extractor (extractors pin)
        parse-value (create-value-parser parameter-definition [pin pname] parser-options)]
    (fn [request]
      (let [pvalue (extractor request parameter-definition)
            pvalue (parse-value pvalue)]
        [pin pname pvalue]))))

(defn create-parsers
  "Creates a list of all parameter parser functions for a request that return a triple of [in out value] when called."
  [request-definition parser-options]
  (map #(create-parser % parser-options) (get request-definition "parameters")))

(defn setup
  "Prepares function calls for parsing parameters during request time."
  [{:keys [requests] :as context} parser-options]
  (let [parsers (into {}
                      (map (fn [[request-key request-definition]]
                             [request-key (create-parsers request-definition parser-options)])
                           requests))]
    (assoc context :parsers parsers)))

(defn serialize-response
  "Serializes the response body according to the Content-Type."
  [request response]
  (let [supported-content-types {"application/json" (fn [body]
                                                      (if (string? body)
                                                        body
                                                        (json/write-str body)))}]
    (if-let [serializer (supported-content-types (get-in response [:headers "Content-Type"]))]
      ; TODO maybe check for allowed "produces" mimetypes and do object validation
      (update-in response [:body] serializer)
      response)))

(defn parse
  "Executes all prepared functions for the request."
  [{:keys [parsers]} next-handler request]
  (try
    (let [parameters (->> (get parsers (-> request :swagger :key))
                          ; execute all parsers of the request
                          (map (fn [parser] (parser request)))
                          ; group by "in"
                          (group-by first)
                          ; restructure to resemble the grouping: map[in][name] = value
                          (map (fn [[parameter-in parameters]]
                                 [(keyword parameter-in) (into {} (map (fn [[pin pname pvalue]]
                                                                         [(keyword pname) pvalue]) parameters))]))
                          (into {}))]
      (log/debug "parameters" parameters)
      (let [response (next-handler (assoc request :parameters parameters))]
        (serialize-response request response)))

    (catch Exception e
      (if (and (instance? ExceptionInfo e) (contains? (ex-data e) :http-code))
        ; nice errors
        (let [{:keys [http-code] :as data} (ex-data e)]
          (api/error http-code (.getMessage e) (:details data)))
        ; unexpected errors
        (do
          (log/error e "internal server error" (str e))
          (api/error 500 "Internal Server Error"))))))
