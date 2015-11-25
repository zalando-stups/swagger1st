(ns io.sarnowski.swagger1st.util.newrelic
  (:require [io.sarnowski.swagger1st.util.api :as api]
            [clojure.tools.logging :as log]
            [io.sarnowski.swagger1st.core :as s1st])
  (:import (com.newrelic.api.agent NewRelic Trace ExtendedRequest HeaderType Response)
           (clojure.lang ExceptionInfo)))

(defn newrelic-request [method uri headers]
  (proxy [ExtendedRequest] []
    (getAttribute [name] nil)
    (getCookieValue [name] nil)                             ; currently not supported
    (getHeader [name] (get headers name))
    (getHeaderType [] (HeaderType/HTTP))
    (getMethod [] method)
    (getParameterNames [] [])
    (getParameterValues [ name] nil)
    (getRemoteUser [] nil)
    (getRequestURI [] uri)))

(defrecord newrelic-response [content-type status headers]
  Response
  (getContentType [_] content-type)
  (getHeaderType [_] (HeaderType/HTTP))
  (setHeader [_ name value] (swap! headers assoc name value))
  (getStatus [_] status)
  (getStatusMessage [_] nil))

(defn setup-http-correlation
  "Provides the NewRelic agent information about the incoming request and the given response in order to correlate
   upstream transactions and also collect generic http information."
  [request response]
  (let [nrreq (newrelic-request (-> request :request-method name .toUpperCase)
                                      (:uri request)
                                      (:headers request))
        nrresp (map->newrelic-response {:content-type (-> response :headers (get "Content-Type"))
                                        :status (:status response)
                                        :headers      (atom {})})]
    ; setup the actual correlation for upstream systems
    (NewRelic/setRequestAndResponse nrreq nrresp)

    (let [headers (merge (:headers response) (deref (:headers nrresp)))]
      (assoc response :headers headers))))

(definterface NewRelicTraceable
  (callWithTrace [transaction-name custom-parameters request callback]))

(deftype NewRelicTracer []
  NewRelicTraceable
  ; create a method with the @Trace annotation
  (^{Trace {:dispatcher true}}
  callWithTrace [_ transaction-name custom-parameters request callback]


    ; set up transaction name
    (NewRelic/setTransactionName nil transaction-name)
    ; add all custom parameters
    (doseq [[key val] (sort-by key custom-parameters)]
      (NewRelic/addCustomParameter (name key) (str val)))

    ; call the actual function
    (try
      (->> (callback)
           (setup-http-correlation request))
      (catch Throwable e
        (let [response (if (and (instance? ExceptionInfo e) (contains? (ex-data e) :http-code))
                         ; nice errors
                         (let [{:keys [http-code] :as data} (ex-data e)]
                           (api/error http-code (.getMessage e) (:details data)))
                         ; unexpected errors
                         (do
                           (log/error e "internal server error" (str e))
                           (api/error 500 "Internal Server Error")))]
          (setup-http-correlation request response))))))

(def newrelic-tracer (NewRelicTracer.))

(defn path-to-str [x]
  (if (keyword? x) (str "{" (name x) "}") x))

(defn swagger-path-to-str
  ([paths] (swagger-path-to-str paths ""))
  ([paths path]
   (if (empty? paths)
     path
     (swagger-path-to-str (butlast paths) (str "/" (path-to-str (last paths)) path)))))

(defn swagger-path [request]
  (swagger-path-to-str
    ; first is method, second is path array
    (-> request (get-in [:swagger :key]) second)))

(defn swagger-parameters [request]
  ; filter out body parameters and merge to flat map
  (let [parameters (get request :parameters)
        filtered (map (fn [[k v]]
                        (if (== k :body) {} v)) parameters)]
    (apply merge filtered)))

(defn tracer
  "A swagger middleware that configures NewRelic agents to track all operations as transactions."
  [context]
  (s1st/chain-handler
    context
    :on-request (fn [context next-handler request]
                  (let [transaction-name (swagger-path request)
                        custom-parameters (swagger-parameters request)]
                    (.callWithTrace newrelic-tracer
                                    transaction-name
                                    custom-parameters
                                    request
                                    #(next-handler request))))))
