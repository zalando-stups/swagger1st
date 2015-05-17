(ns todo.db.atom
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [todo.db :as tododb])
  (:import (java.util UUID)))


(defrecord AtomDatabase [data]
  component/Lifecycle

  (start [this]

    (if data
      (do
        (log/debug "skipping creation of AtomDatabase; already initialized")
        this)

      (do
        (log/info "creating a new AtomDatabase")
        (assoc this :data (atom {})))))

  (stop [this]

    (if-not data
      (do
        (log/debug "skipping shut down of AtomDatabase; not initialized")
        this)

      (do
        (log/info "shutting down AtomDatabase")
        (assoc this :data nil))))


  tododb/Database

  (list-todos [{data :data}]
    @data)

  (add-todo [{data :data} todo-data]
    (log/info "storing new TODO entry:" todo-data)
    (let [id (str (UUID/randomUUID))]
      (swap! data assoc id todo-data)
      id))

  (del-todo [{data :data} id]
    (log/info "deleting TODO entry:" id)
    (swap! data dissoc id)
    nil))


(defn new-atom-db
  "Official constructor for the AtomDatabase."
  []
  (map->AtomDatabase {}))
