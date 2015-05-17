(ns todo.db)

; protocol that databases have to implement

(defprotocol Database
  (list-todos [this] "lists all TODO entries")
  (add-todo [this todo-data] "adds a new TODO entry to the DB")
  (del-todo [this id] "deletes a TODO entry from the DB"))
