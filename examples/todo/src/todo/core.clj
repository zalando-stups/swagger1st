(ns todo.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [todo.db.atom :refer [new-atom-db]]
            [ring.adapter.jetty :as jetty]
            [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.executor :as s1stexec]
            [io.sarnowski.swagger1st.util.api :as s1stapi])
  (:gen-class))

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
              db-mapper (fn [request-definition]
                            (if-let [cljfn (s1stexec/operationId-to-function request-definition)]
                              (fn [request]
                                ; this calls our API function now, with a second "db" parameter
                                (cljfn request db))))

              ; the actual ring setup
              handler (-> (s1st/context :yaml-cp definition)
                          (s1st/ring s1stapi/surpress-favicon-requests)
                          (s1st/discoverer)
                          (s1st/mapper)
                          (s1st/parser)
                          (s1st/executor :resolver db-mapper))]

          ; use jetty as ring implementation
          (assoc this :httpd (jetty/run-jetty handler {:join? false
                                                       :port 8080}))))))

  (stop [this]
    (if-not httpd
      (do
        (log/debug "skipping stop of HTTP; not running")
        this)

      (do
        (log/info "stopping HTTP daemon")
        (.stop httpd)
        (assoc this :httpd nil)))))

(defn new-api
  "Official constructor for the API."
  [definition]
  (map->API {:definition definition}))


(defn new-system
  "The well known creation of a new system. Read the 'component' documentation for more information. Takes the
   Swagger YAML definition and a database-creation function for an implementation of your choice."
  [definition new-db-fn]
  (component/system-map

    ; our database implementation
    :db (new-db-fn)

    ; our API depends on the DB
    :api (component/using
           (new-api definition) [:db])))

(defn -main [& args]
  (log/info "starting TODO service")
  (component/start
    (new-system "todo-api.yaml" new-atom-db)))
