(ns io.sarnowski.swagger1st.parser
  (:require [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [io.sarnowski.swagger1st.mapper :refer [split-path]]
            [clj-time.format :as f])
  (:import (org.joda.time DateTime)
           (java.io PrintWriter)))

(defn- serialize-date-time
  "Serializes a org.joda.time.DateTime to JSON in a compliant way."
  [^DateTime date-time #^PrintWriter out]
  (.print out (-> (f/formatters :date-time)
                  (f/unparse date-time)
                  (json/write-str))))

; add json capability to org.joda.time.DateTime
(extend DateTime json/JSONWriter
  {:-write serialize-date-time})

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
                (if (get definition "pattern")
                  (str " with pattern '" (get definition "pattern") "'")
                  "")
                " because " (apply str (map (fn [v] (if (keyword? v) (name v) v)) reason)) ".")
           {:http-code  400
            :value      value
            :path       path
            :definition definition})))

(comment

  (def primitives {"integer" Number
                   "number"  Number
                   "string"  String
                   "boolean" Boolean})

  (def primitive? (into #{} (keys primitives)))



  (defn parse-value
    "'Casts' a value based on the definition to the concrete form (e.g. string -> integer)."
    [value definition & [path]]
    (let [path (if path path [(get definition "in") (get definition "name")])
          value-type (get definition "type")
          err (partial throw-value-error value definition path)]

      (when-not definition
        (err "it is not defined"))

      (when-not value-type
        value)

      (cond
        (= "object" value-type)
        (if (map? value)
          (do
            ; check all required keys are present
            (let [provided-keys (into #{} (keys value))
                  required-keys (into #{} (map keyword (get definition "required")))]
              (doseq [required-key required-keys]
                (when-not (contains? provided-keys required-key)
                  (err "it misses the key '" (name required-key) "'"))))
            ; traverse into all keys
            (into {}
                  (map (fn [[k v]]
                         [k (parse-value v (get-in definition ["properties" (name k)]) (conj path k))])
                       value)))
          (err "it is not an object"))

        ; check nil after object because object has another notion of required
        (nil? value)
        (if (get definition "required")
          (err "it is required")
          nil)

        (= "array" value-type)
        (if (seq value)
          (let [items-definition (get definition "items")
                items-counter (atom 0)]
            (map (fn [v] (parse-value v items-definition (conj path (swap! items-counter inc)))) value))
          (err "it is not an array"))

        :else
        (do
          (when-not (primitive? value-type)
            ; TODO setup step, not runtime
            (err "its type is not supported"))

          (let [value (if (and (string? value) (not (= "string" value-type)))
                        (if-let [string-transformer (or (get string-transformers (get definition "format"))
                                                        (get string-transformers value-type))]
                          (try
                            (string-transformer value)
                            (catch Exception e
                              (err "it cannot be transformed: " (.getMessage e))))
                          ; TODO setup step, not runtime
                          (err "its format is not supported"))
                        value)]

            (when (and (= "string" value-type) (contains? definition "pattern"))
              ; TODO prepare pattern on setup
              (let [pattern (re-pattern (get definition "pattern"))]
                (when-not (re-matches pattern value)
                  (err "it does not match the given pattern '" (get definition "pattern") "'"))))

            ; TODO checks on minimum, maximum, etc.
            value))))))

(defn extract-parameter-path
  "Extract a parameter from the request path."
  [request definition]
  (let [[_ template-path] (-> request :swagger :key)
        ; TODO split paths before once and only access parsed paths here
        parameters (map (fn [t r] (if (keyword? t) [t r] nil))
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
            (throw (ex-info ("Malformed body. (" (str e)) {:http-code    400
                                                           :content-type content-type}))))
        ; TODO check during setup
        (throw-value-error nil parameter-definition [(get parameter-definition "in") (get parameter-definition "name")] "its content-type is not supported"))
      (throw (ex-info "Content type not allowed."
                      {:http-code             406
                       :content-type          content-type
                       :allowed-content-types allowed-content-types})))))

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
  (let [err (partial throw-value-error value definition path)]
    (if-let [string-transformer (or (get string-transformers (get definition "format"))
                                    (get string-transformers (get definition "type")))]
      (try
        (string-transformer value)
        (catch Exception e
          (err "it cannot be transformed: " (.getMessage e))))
      ; TODO check on setup, not runtime
      (err "its format is not supported"))))

(defmulti create-value-parser
          "Creates a parser function that takes a value and coerces and validates it."
          (fn [definition _]
            (get definition "type")))

(defmethod create-value-parser "object" [definition path]
  (fn [value]
    value))

(defmethod create-value-parser "array" [definition path]
  (fn [value]
    value))

(defmethod create-value-parser "string" [definition path]
  (fn [value]
    (let [value (coerce-string value definition path)]
      value)))

(defmethod create-value-parser "integer" [definition path]
  (fn [value]
    (let [value (coerce-string value definition path)]
      value)))

(defmethod create-value-parser "number" [definition path]
  (fn [value]
    (let [value (coerce-string value definition path)]
      value)))

(defmethod create-value-parser "boolean" [definition path]
  (fn [value]
    (let [value (coerce-string value definition path)]
      value)))

(defmethod create-value-parser :default [definition path]
  ; ignore this value and just pass through
  (fn [value]
    value))

(defn create-parser
  "Creates a parsing function for the given parameter definition."
  [parameter-definition]
  (let [pin (get parameter-definition "in")
        pname (get parameter-definition "name")

        parameter-definition (if (= "body" pin) (get parameter-definition "schema") parameter-definition)

        extractor (extractors pin)
        parse-value (create-value-parser parameter-definition [pin pname])]
    (fn [request]
      (let [pvalue (extractor request parameter-definition)
            pvalue (parse-value pvalue)]
        [pin pname pvalue]))))

(defn create-parsers
  "Creates a list of all parameter parser functions for a request that return a triple of [in out value] when called."
  [request-definition]
  (map (fn [parameter-definition]
         (create-parser parameter-definition))
       (get request-definition "parameters")))

(defn setup
  "Prepares function calls for parsing parameters during request time."
  [{:keys [requests] :as context}]
  (let [parsers (into {}
                      (map (fn [[request-key request-definition]]
                             [request-key (create-parsers request-definition)])
                           requests))]
    (assoc context :parsers parsers)))

(defn parse
  "Executes all prepared functions for the request."
  [{:keys [parsers]} next-handler request]
  (let [parameters (->> (get parsers (-> request :swagger :key))
                        ; execute all parsers of the request
                        (map (fn [parser] (parser request)))
                        ; group by "in"
                        (group-by first)
                        ; restructure to resemble the grouping: map[in][name] = value
                        (map (fn [[parameter-in parameters]]
                               [parameter-in (into {} (map (fn [[pin pname pvalue]]
                                                             [pname pvalue]) parameters))]))
                        (into {}))]
    (next-handler (assoc request :parameters parameters))))