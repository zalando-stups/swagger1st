(ns io.sarnowski.swagger1st.parser-test
  (:require [clojure.test :refer :all]
            [io.sarnowski.swagger1st.parser :as p]
            [clj-time.core :as t]
            [clojure.data.json :as json])
  (:import (java.io StringReader)))

(deftest parse-values
  (is (= nil (p/parse-value nil {"type" "string"})))
  (is (= "foo" (p/parse-value "foo" {"type" "string"})))
  (is (= 123 (p/parse-value "123" {"type" "integer"})))
  (is (= "123" (p/parse-value "123" {"type" "string"})))
  (is (= 0.5 (p/parse-value "0.5" {"type" "number"})))
  (is (= (t/date-time 2015 4 28) (p/parse-value "2015-04-28" {"type" "string" "format" "date"})))
  (is (= (t/date-time 2015 4 28 10 56 12 98) (p/parse-value "2015-04-28T12:56:12.098+02:00" {"type" "string" "format" "date-time"}))))

(deftest extract-path-parameters
  (is (= "baz"
         (p/extract-parameter-path
           {:uri     "/foo/baz"
            :swagger {:key ["foo" :bar]}}
           {"name" "bar"
            "type" "string"}))))

(deftest extract-query-parameters
  (is (= "baz"
         (p/extract-parameter-query
           {:uri          "/foo"
            :query-params {"bar" "baz"}
            :swagger      {:key ["foo"]}}
           {"name" "bar"
            "type" "string"}))))

(deftest extract-header-parameters
  (is (= "baz"
         (p/extract-parameter-header
           {:uri     "/foo"
            :headers {"bar" "baz"}
            :swagger {:key ["foo"]}}
           {"name" "bar"
            "type" "string"}))))

(deftest extract-form-parameters
  (is (= "baz"
         (p/extract-parameter-form
           {:uri         "/foo"
            :form-params {"bar" "baz"}
            :swagger     {:key ["foo"]}}
           {"name" "bar"
            "type" "string"}))))

(defn json-body
  "Generates a reader that can be used to read serialized JSON."
  [data]
  (StringReader. (json/write-str data)))

(deftest extract-body-parameter
  (is (= {:foo    "bar"
          :tested (t/date-time 2015 4 28 15 13 39 123)}
         (p/extract-parameter-body

           {:uri     "/foo"
            :headers {"content-type" "application/json"}
            :swagger {:key     ["foo"]
                      :request {"consumes" ["application/json"]}}
            :body    (json-body
                       {:foo    "bar"
                        :tested (t/date-time 2015 4 28 15 13 39 123)})}

           {"name" "testmap"
            "type" "object"}))))
