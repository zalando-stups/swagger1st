(ns {{name}}.http
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.executor :as s1stexec]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]))

(defrecord HTTP [jetty db]
   component/Lifecycle

   (start [this]
      (println "Starting HTTP component...")
      ; the resolver-fn is the magic where you can pass in whatever you want to the API implementing functions,
      ; for example the DB dependency of this component. You could also restructure the parameters alltogether or
      ; define your own dynamic mapping scheme.
      (let [resolver-fn (fn [request-definition]
                            (if-let [cljfn (s1stexec/operationId-to-function request-definition)]
                              (fn [request]
                                ; Here we are actually calling our clojure functions with the parameters map in the first
                                ; argument and our DB dependency as the second argument.
                                (cljfn request db))))

            handler (-> (s1st/context :yaml-cp "{{sanitized}}-api.yaml")
                        (s1st/discoverer)
                        (s1st/mapper)
                        (s1st/parser)
                        (s1st/executor :resolver resolver-fn))]

        (assoc this :jetty (jetty/run-jetty handler {:join? false
                                                     :port 3000}))))

   (stop [this]
     (.stop (:jetty this))
     (assoc this :jetty nil)))