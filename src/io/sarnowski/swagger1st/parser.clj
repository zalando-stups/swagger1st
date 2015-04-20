(ns io.sarnowski.swagger1st.parser
  (:require [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [io.sarnowski.swagger1st.mapper :refer [split-path]])
  (:import (java.text DateFormat)))

(defn- coerce-item-value
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
        (coerce-item-value definition))))

(defn- extract-parameter-query
  "Extract a parameter from the request url."
  [{query-params :query-params} definition]
  (-> (get query-params (get definition "name"))
      (coerce-item-value definition)))

(defn- extract-parameter-header
  "Extract a parameter from the request headers."
  [{headers :headers} definition]
  (-> (get headers (get definition "name"))
      (coerce-item-value definition)))

(defn- extract-parameter-form
  "Extract a parameter from the request body form."
  [{form-params :form-params} definition]
  (-> (get form-params (get definition "name"))
      (coerce-item-value definition)))

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

(defn parse
  "A swagger middleware that uses a swagger definition for parsing parameters and crafting responses."
  [context next-handler request]
  (let [swagger-request (:swagger-request request)]
    (let [parameter-groups (group-by first
                                     (map (fn [definition]
                                            (extract-parameter request definition))
                                          (get swagger-request "parameters")))
          parameter-groups (into {}
                                 (map (fn [[group parameters]]
                                        [group (into {} (map (fn [[_ k v]] [k v]) parameters))])
                                      parameter-groups))]
      (next-handler (assoc request :parameters parameter-groups)))))
