(ns {{name}}.core
  (:require [com.stuartsierra.component :as component]
            [{{name}}.http :as http]
            [{{name}}.db :as db])
  (:gen-class))

(defn -main [& args]

   (let [system (component/system-map
                  :db (db/map->DB {})
                  :http (component/using
                          (http/map->HTTP {}) [:db]))]

     (component/start system)))