(defproject {{name}} "0.1.0-SNAPSHOT"
  :description "TODO provide a description"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [ring "1.4.0"]
                 [io.sarnowski/swagger1st "0.15.0-SNAPSHOT"]]

  :plugins [[lein-ring "0.9.2"]]

  :ring {:handler {{name}}.core/app})
