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

(def primitives {"integer" Number
                 "number"  Number
                 "string"  String
                 "boolean" Boolean})

(def primitive? (into #{} (keys primitives)))

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
          value)))))

(defn extract-parameter-path
  "Extract a parameter from the request path."
  [request definition]
  (let [swagger-key (-> request :swagger :key)
        ; TODO split paths before once and only access parsed paths here
        parameters (map (fn [t r] (if (keyword? t) [t r] nil))
                        swagger-key
                        (split-path (:uri request)))
        parameters (->> parameters
                        (remove nil?)
                        (into {}))]
    (-> (get parameters (keyword (get definition "name")))
        (parse-value definition))))

(defn extract-parameter-query
  "Extract a parameter from the request url."
  [{query-params :query-params} definition]
  (-> (get query-params (get definition "name"))
      (parse-value definition)))

(defn extract-parameter-header
  "Extract a parameter from the request headers."
  [{headers :headers} definition]
  (-> (get headers (get definition "name"))
      (parse-value definition)))

(defn extract-parameter-form
  "Extract a parameter from the request body form."
  [{form-params :form-params} definition]
  (-> (get form-params (get definition "name"))
      (parse-value definition)))

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
        (parse-value
          (try
            (deserialize-fn (:body request))
            (catch Exception e
              (throw (ex-info ("Malformed body. (" (str e)) {:http-code    400
                                                             :content-type content-type}))))
          parameter-definition)
        ; TODO check during setup
        (throw-value-error nil parameter-definition [(get parameter-definition "in") (get parameter-definition "name")] "its content-type is not supported"))
      (throw (ex-info "Content type not allowed."
                      {:http-code             406
                       :content-type          content-type
                       :allowed-content-types allowed-content-types})))))

(defn extract-parameter
  "Extracts a parameter from the request according to the definition."
  [request definition]
  (let [in (keyword (get definition "in"))
        extractors {:path   extract-parameter-path
                    :query  extract-parameter-query
                    :header extract-parameter-header
                    :form   extract-parameter-form
                    :body   extract-parameter-body}
        extractor (in extractors)
        parameter [in (keyword (get definition "name")) (extractor request definition)]]
    (log/trace "parameter:" parameter)
    parameter))

(defn parse
  "A swagger middleware that uses a swagger definition for parsing parameters and crafting responses."
  [context next-handler request]
  (let [swagger-request (-> request :swagger :request)]
    (let [parameter-groups (group-by first
                                     (map (fn [definition]
                                            (extract-parameter request definition))
                                          (get swagger-request "parameters")))
          parameter-groups (into {}
                                 (map (fn [[group parameters]]
                                        [group (into {} (map (fn [[_ k v]] [k v]) parameters))])
                                      parameter-groups))]
      (next-handler (assoc request :parameters parameter-groups)))))
