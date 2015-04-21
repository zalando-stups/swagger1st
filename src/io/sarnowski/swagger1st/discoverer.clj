(ns io.sarnowski.swagger1st.discoverer
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [ring.util.response :as r]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]))

(defn- swaggerui-template
  "Loads the swagger ui template (index.html) and replaces certain keywords."
  [definition definition-url]
  (let [template (slurp (io/resource "io/sarnowski/swagger1st/swaggerui/index.html"))
        vars {"$TITLE$"      (get-in definition ["info" "title"])
              "$DEFINITION$" definition-url}]
    (reduce (fn [template [var val]] (string/replace template var val)) template vars)))

(defn process
  "Creates a filter to handle UI and other discovery requests."
  [request definition {:keys [discovery-path definition-path ui-path overwrite-host?] :as config}]
  (let [path (:uri request)]
    (cond
      (= discovery-path path) (-> (r/response (json/write-str {:schema_url  definition-path
                                                               :schema_type "swagger-2.0"
                                                               :ui_url      ui-path}))
                                  (r/header "Content-Type" "application/json"))
      (= definition-path path) (-> (r/response
                                     (json/write-str
                                       (if overwrite-host?
                                         (assoc definition "host" (or
                                                                    (-> request :headers (get "host"))
                                                                    (-> request :server-name)
                                                                    (-> definition "host")))
                                         definition)))
                                   (r/header "Content-Type" "application/json"))
      (= ui-path path) (-> (r/response (swaggerui-template definition definition-path))
                           (r/header "Content-Type" "text/html"))
      (.startsWith path ui-path) (let [path (.substring path (count ui-path))]
                                   (-> (str "io/sarnowski/swagger1st/swaggerui/" path)
                                       io/resource
                                       io/input-stream
                                       r/response)))))

(defn discover [config context next-handler request]
  (if-let [response (process request (:definition context) config)]
    response
    (next-handler request)))
