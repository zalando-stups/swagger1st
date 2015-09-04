(defproject helloworld "0.1.0-SNAPSHOT"
  :description "An example application for swagger1st using the most simplic approach."

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [ring "1.4.0"]
                 [io.sarnowski/swagger1st "0.15.0-SNAPSHOT"]]

  :plugins [[lein-ring "0.9.2"]]

  :ring {:handler helloworld/app})
