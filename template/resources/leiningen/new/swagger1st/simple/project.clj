(defproject {{name}} "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring "1.5.0"]
                 [org.zalando/swagger1st "0.22.0"]]

  :plugins [[lein-ring "0.9.7"]]

  :ring {:handler {{name}}.core/app})
