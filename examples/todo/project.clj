(defproject io.sarnowski/swagger1st-todo "0.1.0-SNAPSHOT"
  :description "An example application for swagger1st integrating with the component framework."

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring "1.3.2"]
                 [io.sarnowski/swagger1st "0.3.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [http-kit "2.1.16"]]

  :main ^:skip-aot todo.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
