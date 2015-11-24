(ns io.sarnowski.swagger1st.util.newrelic
  (:import [com.newrelic.agent NewRelic Trace]))

(definterface NewRelicTraceable
  (callWithTrace [transaction-name custom-parameters callback]))

(deftype NewRelicTracer []
  NewRelicTraceable
    ; create a method with the @Trace annotation
    (^{Trace {:dispatcher true}}
      callWithTrace [_ transaction-name custom-parameters callback]
        ; set up transaction name
        (NewRelic/setTransactionName nil transaction-name)
        ; add all custom parameters
        (doseq [[key val] (sort-by key custom-parameters)]
          (NewRelic/addCustomParameter (name key) (str val)))
        ; call the actual function
        (callback)))

(def newrelic-tracer (NewRelicTracer.))

(defn path-to-str [x]
  (if (keyword? x) (str "{" (name x) "}") x))

(defn swagger-path-to-str
  ([[x]] x)
  ([[x & xs]] (str "/" (path-to-str x) (recur xs))))

(defn swagger-path [request]
  ; first is method, second is path array
  (-> request (get-in :swagger :key) second))

(defn swagger-parameters [request]
  ; filter out body parameters and merge to flat map
  (let [parameters (get request :parameters)
        filtered (map (fn [[k v]]
                        (if (== k :body) {} v)) parameters)]
    (apply merge filtered)))

(defn tracer
  "A swagger middleware that configures NewRelic agents to track all operations as transactions."
  [context]
  (chain-handler
    context
    :on-request (fn [context next-handler request]
                  (let [transaction-name (swagger-path request)
                        custom-parameters (swagger-parameters request)]
                    (try
                      (.callWithTrace newrelic-tracer
                                      transaction-name
                                      custom-parameters
                                      #(next-handler request))
                      (catch Throwable e
                        (NewRelic/noticeError e)
                        (throw e)))))))
