(ns io.sarnowski.swagger1st.performance-test
  (:require [clojure.test :refer :all]
            [criterium.core :as c]
            [ring.middleware.params :refer [wrap-params]]
            [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [ring.util.response :as r]
            [ring.mock.request :as mock])
  (:import (org.apache.logging.log4j LogManager Level)
           (org.apache.logging.log4j.core.config Configuration LoggerConfig)
           (org.apache.logging.log4j.core LoggerContext)))

(defn simple-response [request]
  (-> (r/response {:foo        "bar"
                   :some       ["a" "b" "c"]
                   :parameters (:parameters request)})
      (r/content-type "application/json")))

(defn- set-log-level!
  "Changes the log level of the log4j2 root logger."
  [level]
  (let [^Level level (Level/getLevel level)
        ^LoggerContext ctx (LogManager/getContext false)
        ^Configuration config (.getConfiguration ctx)
        ^LoggerConfig logger-config (.getLoggerConfig config LogManager/ROOT_LOGGER_NAME)]
    (.setLevel logger-config level)
    (.updateLoggers ctx config)
    ; TODO doesn't work currently
    (println logger-config "level set to" level)))

(defn generate-definition []
  (let [definition {"swagger"  "2.0"
                    "info"     {"title"   "Performance Tests"
                                "version" "1.0"}
                    "produces" ["application/json"]
                    "consumes" ["application/json"]
                    "paths"    {"/" {"get" {"operationId" "io.sarnowski.swagger1st.performance-test/simple-response"
                                            "responses"   {200 {"description" "simple response"}}}}}}
        requests [(mock/request :get "/")]]
    [definition requests]))

(defn microseconds [num]
  (* num 0.000001))

(deftest ^:performance performance
  (set-log-level! "INFO")

  (let [[definition requests] (generate-definition)
        requests (atom (cycle requests))
        next-request (fn []
                       (first (swap! requests rest)))
        app (-> (s1st/context :direct definition)
                (s1st/discoverer)
                (s1st/ring wrap-params)
                (s1st/mapper)
                (s1st/parser)
                (s1st/protector {"oauth2" (s1stsec/allow-all)})
                (s1st/executor))

        execute-request (fn []
                          (app (next-request)))

        ; do benchmarks!
        result (c/with-progress-reporting (c/benchmark (execute-request) {}))

        [mean-time] (:mean result)]
    (c/report-result result :verbose)

    (is (< mean-time (microseconds 200)))

    (set-log-level! "TRACE")))