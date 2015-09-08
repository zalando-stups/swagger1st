(ns {{name}}.db
  (:require [com.stuartsierra.component :as component]))

(defrecord DB [counter]
   component/Lifecycle

   (start [this]
     (println "Starting DB component...")
     (assoc this :counter (atom 0)))

   (stop [this]
     (assoc this :counter nil)))

(defn inc-counter [db]
  (-> db (get :counter) (swap! inc)))