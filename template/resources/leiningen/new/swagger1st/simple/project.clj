(defproject {{name}} "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [ring "1.4.0"]
                 [io.sarnowski/swagger1st "0.21.0"]]

  :plugins [[lein-ring "0.9.6"]]

  :ring {:handler {{name}}.core/app})
