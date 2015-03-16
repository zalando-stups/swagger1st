(ns todo.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [todo.db.atom :refer [new-atom-db]]
            [todo.api :refer [new-api]])
  (:gen-class))

(defn new-system [definition new-db]
  (component/system-map

    ; our database implementation
    :db (new-db)

    ; our API depends on the DB
    :api (component/using
           (new-api definition) [:db])))

(defn -main [& args]
  (log/info "starting TODO service")
  (component/start
    (new-system "todo-api.yaml" new-atom-db)))
