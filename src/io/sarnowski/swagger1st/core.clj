(ns io.sarnowski.swagger1st.core
  (:require [clojure.data.json :as json]
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

(defn- create-call-defs [definition]
  ; for every endpoint, create a call-def (fully calculated definition)
  [{:fn 'a-fn}])

(defn- request-matches? [call-def request]
  ; method, path, ...
  true)

(defn- create-call-def-lookup [definition]
  (let [call-defs (create-call-defs definition)]
    (fn [request]
      (first (filter #(request-matches? % request) call-defs)))))

;;; The middleware

(defn swagger-routing
  "A ring middleware that uses a swagger definition for routing."
  [chain-handler swagger-definition-type swagger-definition]
  (let [definition (schema/validate s/swagger-schema
                                    (load-swagger-definition swagger-definition-type swagger-definition))
        lookup-call-def (create-call-def-lookup definition)]

    ; actual runtime function
    (fn [request]
      (if-let [call-def (lookup-call-def request)]
        ; found the definition, call the connected function
        ((:fn call-def) call-def)
        ; definition not found, skip for our middleware
        (chain-handler request)))))
