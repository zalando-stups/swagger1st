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
        (assoc this :data (atom [])))))

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

  (add-todo [{data :data} title]
    (log/info "storing new TODO entry:" title)
    (swap! data conj
           {:id    (str (UUID/randomUUID))
            :title title}))

  (del-todo [{data :data} id]
    (log/info "deleting TODO entry:" id)
    (swap! data (fn [data]
                  (into {} (remove #(= (:id data) id) data))))))


(defn new-atom-db
  "Official constructor for the AtomDatabase."
  []
  (map->AtomDatabase {}))
