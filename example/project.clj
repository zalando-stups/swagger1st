(defproject io.sarnowski/swagger1st-example "0.1.0-SNAPSHOT"
  :description "An example application for swagger1st."

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring "1.3.2"]
                 [io.sarnowski/swagger1st "0.1.0-SNAPSHOT"]]

  :plugins [[lein-ring "0.9.2"]]

  :ring {:handler example.core/app})
