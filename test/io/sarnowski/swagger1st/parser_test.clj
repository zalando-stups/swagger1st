(ns io.sarnowski.swagger1st.parser-test
  (:require [clojure.test :refer :all]
            [io.sarnowski.swagger1st.parser :as p]
            [clj-time.core :as t]
            [clojure.data.json :as json])
  (:import (java.io StringReader)))

(defn parse [value definition]
  (let [parser (p/create-value-parser definition ["test"])]
    (parser value)))

; TODO test error cases

(deftest string-values
  (is (= nil (parse nil {"type" "string"})))
  (is (= "foo" (parse "foo" {"type" "string"})))
  (is (= "123" (parse "123" {"type" "string"})))

  (is (= (t/date-time 2015 4 28)
         (parse "2015-04-28" {"type"   "string"
                              "format" "date"})))
  (is (= (t/date-time 2015 4 28 10 56 12 98)
         (parse "2015-04-28T12:56:12.098+02:00" {"type"   "string"
                                                 "format" "date-time"})))

  (is (= "foo" (parse "foo" {"type"    "string"
                             "pattern" "[a-z]+"}))))

(deftest integer-values
  (is (= 123 (parse "123" {"type" "integer"})))

  (is (= 123 (parse "123" {"type"    "integer"
                           "format " "int32"})))
  (is (= 123 (parse "123" {"type"    "integer"
                           "format " "int64"}))))

(deftest number-values
  (is (= 0.5 (parse "0.5" {"type" "number"})))

  (is (= 0.5 (parse "0.5" {"type"   "number"
                           "format" "float"})))
  (is (= 0.5 (parse "0.5" {"type"   "number"
                           "format" "double"}))))

(deftest boolean-values
  (is (= true (parse "true" {"type" "boolean"})))
  (is (= false (parse "false" {"type" "boolean"}))))

(deftest array-values
  (is (= ["foo" "bar"] (parse ["foo" "bar"]
                              {"type"  "array"
                               "items" {"type" "string"}})))

  (is (= [5 6] (parse [5 6]
                      {"type"  "array"
                       "items" {"type" "integer"}})))

  (is (= [(t/date-time 2015 4 28 10 56 12 98)]
         (parse ["2015-04-28T12:56:12.098+02:00"]
                {"type"  "array"
                 "items" {"type"   "string"
                          "format" "date-time"}}))))

(deftest object-values
  (is (= {:foo "bar"} (parse {:foo "bar"}
                             {"type"       "object"
                              "properties" {"foo" {"type" "string"}}})))

  (is (= {:foo (t/date-time 2015 4 28 10 56 12 98)}
         (parse {:foo "2015-04-28T12:56:12.098+02:00"}
                {"type"       "object"
                 "properties" {"foo" {"type"   "string"
                                      "format" "date-time"}}}))))

(deftest extract-path-parameters
  (is (= "baz"
         (p/extract-parameter-path
           {:uri     "/foo/baz"
            :swagger {:key ["get" ["foo" :bar]]}}
           {"name" "bar"}))))

(deftest extract-query-parameters
  (is (= "baz"
         (p/extract-parameter-query
           {:uri          "/foo"
            :query-params {"bar" "baz"}}
           {"name" "bar"}))))

(deftest extract-header-parameters
  (is (= "baz"
         (p/extract-parameter-header
           {:uri     "/foo"
            :headers {"bar" "baz"}}
           {"name" "bar"}))))

(deftest extract-form-parameters
  (is (= "baz"
         (p/extract-parameter-form
           {:uri         "/foo"
            :form-params {"bar" "baz"}}
           {"name" "bar"}))))

(defn json-body
  "Generates a reader that can be used to read serialized JSON."
  [data]
  (StringReader. (json/write-str data)))

(deftest extract-body-parameter
  (is (= {:foo "bar"}
         (p/extract-parameter-body
           {:uri     "/foo"
            :headers {"content-type" "application/json"}
            :swagger {:request {"consumes" ["application/json"]}}
            :body    (json-body
                       {:foo "bar"})}
           {"name" "testmap"}))))
