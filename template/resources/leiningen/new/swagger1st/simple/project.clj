(defproject {{name}} "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring "1.6.3"]
                 [org.zalando/swagger1st "0.26.0"]]

  :plugins [[lein-ring "0.12.5"]]

  :ring {:handler {{name}}.core/app})
