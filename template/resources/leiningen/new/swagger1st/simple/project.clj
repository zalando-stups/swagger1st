(defproject {{name}} "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring "1.6.1"]
                 [org.zalando/swagger1st "0.25.0"]]

  :plugins [[lein-ring "0.12.0"]]

  :ring {:handler {{name}}.core/app})
