(ns todo.api
  (:require [ring.util.response :as r]
            [clojure.tools.logging :as log]
            [todo.db :as db]))

;;; the real business logic! mapped in the todo-api.yaml

(defn get-todo-entries [request db]
  (let [todos (db/list-todos db)]
    (-> (map (fn [[id data]] (merge data {:id id})) todos)
        (r/response)
        (r/content-type "application/json"))))

(defn add-todo-entry [request db]
  (log/info "adding new TODO entry")
  (let [id (db/add-todo db (get-in request [:parameters :body :todo]))]
    (-> (r/response {"id" id})
        (r/content-type "application/json"))))

(defn delete-todo-entry [request db]
  (log/info "deleting TODO entry")
  (db/del-todo db (get-in request [:parameters :query :id]))
  (r/response nil))
