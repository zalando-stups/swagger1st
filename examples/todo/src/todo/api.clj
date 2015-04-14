(ns todo.api
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as r]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as httpkit]
            [clojure.tools.logging :as log]
            [todo.db :as db]))

; 'definition' will be configured during instantiation
; 'httpd' is the internal state of the HTTP server
; 'db' will be injected via the lifecycle before start
(defrecord API [definition httpd db]
  component/Lifecycle

  (start [this]
    (if httpd
      (do
        (log/debug "skipping start of HTTP; already running")
        this)

      (do
        (log/info "starting HTTP daemon for API" definition)
        (let [; our custom mapper that adds the 'db' as a second parameter to all API functions
              db-mapper (fn [operationId]
                          (if-let [api-fn (s1st/map-function-name operationId)]
                            (fn [request] (api-fn request db))))

              ; the actual ring setup
              handler (-> (s1st/swagger-context ::s1st/yaml-cp definition)
                          (s1st/swagger-ring wrap-params)
                          (s1st/swagger-mapper)
                          (s1st/swagger-discovery)
                          (s1st/swagger-parser)
                          (s1st/swagger-validator)
                          (s1st/swagger-executor :mappers [db-mapper]))]

          ; use httpkit as ring implementation
          (assoc this :httpd (httpkit/run-server handler {:port 8080}))))))

  (stop [this]
    (if-not httpd
      (do
        (log/debug "skipping stop of HTTP; not running")
        this)

      (do
        (log/info "stopping HTTP daemon")
        (httpd :timeout 100)
        (assoc this :httpd nil)))))

(defn new-api
  "Official constructor for the API."
  [definition]
  (map->API {:definition definition}))


;;; the real business logic! mapped in the todo-api.yaml

(defn get-todo-entries [request db]
  (let [todos (db/list-todos db)]
    (-> (r/response todos)
        (r/content-type "application/json")
        (r/status 200))))

(defn add-todo-entry [request db]
  (log/info "adding new TODO entry")
  (db/add-todo db (get-in request [:parameters :body :title]))
  (-> (r/response nil)
      (r/status 200)))

(defn delete-todo-entry [request db]
  (log/info "deleting TODO entry")
  (db/del-todo db (get-in request [:parameters :query :id]))
  (-> (r/response nil)
      (r/status 200)))
