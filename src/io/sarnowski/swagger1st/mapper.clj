(ns io.sarnowski.swagger1st.mapper
  (:require [ring.util.response :as r]
            [clojure.walk :as walk]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [io.sarnowski.swagger1st.util.api :as api]))

(defn get-definition
  "Resolves a $ref reference to its content."
  [r d]
  (let [path (rest (string/split r #"/"))]
    (get-in d path)))

(defn denormalize-refs
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

(defn inherit-map
  "Merges a map from parent to definition, overwriting keys with definition."
  [definition parent-definition map-name]
  (let [m (get definition map-name)
        pm (get parent-definition map-name)
        merged (merge pm m)]
    (assoc definition map-name merged)))

(defn inherit-list
  "Denormalizes a collection, using the parent or replacing it with definition."
  [definition parent-definition col-name]
  (assoc definition col-name
                    (if-let [col (get definition col-name)]
                      col
                      (get parent-definition col-name))))

(defn conj-if-not
  "Conjoins x to col if test-fn doesn't find an existing entry in col."
  [test-fn col x & xs]
  (let [col (if (empty? (filter (fn [y] (test-fn x y)) col))
              (conj col x)
              col)]
    (if xs
      (recur test-fn col (first xs) (next xs))
      col)))

(defn inherit-list-elements
  "Denormalizes a collection, replacing entries that are equal."
  [definition parent-definition col-name if-not-fn]
  (assoc definition col-name
                    (let [pd (get parent-definition col-name)
                          d (get definition col-name)]
                      (remove nil?
                              (conj-if-not if-not-fn d (first pd) (next pd))))))

(defn keys-equal?
  "Compares two maps if both have the same given keys."
  [x y ks]
  (every? (fn [k] (= (get x k) (get y k))) ks))

(defn inherit-mimetypes
  "Inherit 'consumes' and 'produces' mimetypes if not defined."
  [definition parent-definition]
  (-> definition
      (inherit-list parent-definition "consumes")
      (inherit-list parent-definition "produces")
      (inherit-list parent-definition "security")))

(defn inherit-path-spec
  "Denormalizes inheritance of parameters etc. from the path to operation."
  [definition parent-definition]
  (-> definition
      (inherit-mimetypes parent-definition)
      (inherit-list-elements parent-definition "parameters" (fn [x y] (keys-equal? x y ["name" "in"])))
      (inherit-map parent-definition "responses")
      (inherit-list parent-definition "security")))

(defn split-path
  "Splits a / separated path into its segments."
  [path]
  (let [split (fn [^String s] (.split s "/"))]
    (-> path split rest)))

(defn variable-to-keyword
  "Replaces a variable path segment (like /{username}/) with the variable name as keyword (like :username)."
  [seg]
  (if-let [variable-name (second (re-matches #"\{(.*)\}" seg))]
    ; use keywords for variable names
    (keyword variable-name)
    ; no variable found, return original segment
    seg))

(defn create-request-tuple
  "Generates easier to digest request tuples from operations and paths."
  [operation operation-definition path path-definition]
  (let [keyword-path (->> path split-path (map variable-to-keyword))
        definition (inherit-path-spec
                     operation-definition
                     path-definition)]
    [[operation keyword-path] definition]))

; TODO flatten 'allOf' definitions
(defn extract-requests
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

(defn create-requests
  "Creates a map of 'request-key' -> 'swagger-definition' entries. The request-key can be used to efficiently lookup
   requests. The swagger-definition contains denormalized information about the request specification (all refs and
   inheritance is denormalized)."
  [definition]
  (-> definition
      denormalize-refs
      extract-requests))

(defn path-machtes?
  "Matches a template path with a real path. Paths are provided as collections of their segments. If the template has
   a keyword value, it is a dynamic segment."
  [path-template path-real]
  (when (= (count path-template) (count path-real))
    (let [pairs (map vector path-template path-real)
          pair-matches? (fn [[t r]] (or (keyword? t) (= t r)))]
      (every? pair-matches? pairs))))

(defn request-matches?
  "Checks if the given request matches a defined swagger-request."
  [[operation path-template] request]
  (and (= operation (-> request :request-method name))
       (path-machtes? path-template (-> request :uri split-path))))

(defn lookup-request
  "Creates a function that can do efficient lookups of requests."
  [requests request]
  (->> requests
       (filter (fn [[template _]] (request-matches? template request)))
       ; if we have multiple matches then its not well defined, just choose the first
       first))

(defn setup
  [{:keys [definition] :as context}]
  (log/debug "definition:" definition)
  (let [requests (create-requests definition)]
    (log/debug "requests:" requests)
    (assoc context :requests requests)))

(defn correlate [{:keys [requests]} next-handler request]
  (let [[key swagger-request] (lookup-request requests request)]
    (if (nil? swagger-request)
      (api/error 404 (str (.toUpperCase (-> request :request-method name)) " " (-> request :uri) " not found."))
      (let [request (-> request
                          (assoc-in [:swagger :request] swagger-request)
                          (assoc-in [:swagger :key] key))]
          (log/debug "request" key "->" swagger-request)
          (next-handler request)))))
