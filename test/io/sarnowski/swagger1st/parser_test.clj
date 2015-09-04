(ns io.sarnowski.swagger1st.parser-test
  (:require [clojure.test :refer :all]
            [io.sarnowski.swagger1st.parser :as p]
            [clj-time.core :as t]
            [clojure.data.json :as json])
  (:import (java.io StringReader)
           (clojure.lang ExceptionInfo)))

(defn parse
  "Parses a value according to its definition."
  [value definition & {:keys [] :as parser-options}]
  (let [parser (p/create-value-parser definition ["test"] parser-options)]
    (parser value)))

(defmacro rejected
  "Executes the expressions and returns true, if an ExceptionInfo with http-code 400 was thrown."
  [& exp]
  `(try
     ~@exp
     false
     (catch ExceptionInfo e#
       (if (= 400 (:http-code (ex-data e#)))
         "rejected"
         false))))

(deftest string-values
  ; real strings
  (is (= nil (parse nil {"type" "string"})))
  (is (= "foo" (parse "foo" {"type" "string"})))
  (is (= "123" (parse "123" {"type" "string"})))

  ; dates
  (is (= (t/date-time 2015 4 28)
         (parse "2015-04-28" {"type"   "string"
                              "format" "date"})))
  (is (= (t/date-time 2015 4 28 10 56 12 98)
         (parse "2015-04-28T12:56:12.098+02:00" {"type"   "string"
                                                 "format" "date-time"})))

  ; pattern matching
  (is (= "foo" (parse "foo" {"type"    "string"
                             "pattern" "[a-z]+"})))
  (is (rejected (parse "foo" {"type"    "string"
                              "pattern" "[A-Z]"})))

  ; sizes
  (is (= "foo" (parse "foo" {"type"      "string"
                             "minLength" 3})))
  (is (rejected (parse "foo" {"type"      "string"
                              "minLength" 4})))

  (is (= "foo" (parse "foo" {"type"      "string"
                             "maxLength" 3})))
  (is (rejected (parse "foo" {"type"      "string"
                              "maxLength" 2}))))

(deftest integer-values
  (is (= 123 (parse "123" {"type" "integer"})))

  (is (= 123 (parse "123" {"type"    "integer"
                           "format " "int32"})))
  (is (= 123 (parse "123" {"type"    "integer"
                           "format " "int64"})))
  ; sizes, minimum
  (is (= 123 (parse "123" {"type"    "integer"
                           "minimum" 123})))
  (is (rejected (parse "123" {"type"    "integer"
                              "minimum" 124})))
  (is (= 123 (parse "123" {"type"             "integer"
                           "minimum"          122
                           "exclusiveMinimum" true})))
  (is (rejected (parse "123" {"type"             "integer"
                              "minimum"          123
                              "exclusiveMinimum" true})))

  ; sizes, maximum
  (is (= 123 (parse "123" {"type"    "integer"
                           "maximum" 123})))
  (is (rejected (parse "123" {"type"    "integer"
                              "maximum" 122})))
  (is (= 123 (parse "123" {"type"             "integer"
                           "maximum"          124
                           "exclusiveMaximum" true})))
  (is (rejected (parse "123" {"type"             "integer"
                              "maximum"          123
                              "exclusiveMaximum" true}))))

(deftest number-values
  (is (= 0.5 (parse "0.5" {"type" "number"})))

  (is (= 0.5 (parse "0.5" {"type"   "number"
                           "format" "float"})))
  (is (= 0.5 (parse "0.5" {"type"   "number"
                           "format" "double"})))
  ; see integer-values for validation tests as they are the same
  )

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
                          "format" "date-time"}})))

  ; sizes
  (is (= ["foo" "bar"] (parse ["foo" "bar"]
                              {"type"     "array"
                               "items"    {"type" "string"}
                               "minItems" 2})))
  (is (rejected (parse ["foo" "bar"]
                       {"type"     "array"
                        "items"    {"type" "string"}
                        "minItems" 3})))

  (is (= ["foo" "bar"] (parse ["foo" "bar"]
                              {"type"     "array"
                               "items"    {"type" "string"}
                               "maxItems" 2})))
  (is (rejected (parse ["foo" "bar"]
                       {"type"     "array"
                        "items"    {"type" "string"}
                        "maxItems" 1})))

  ; unique items
  (is (= ["foo" "bar"] (parse ["foo" "bar"]
                              {"type"        "array"
                               "items"       {"type" "string"}
                               "uniqueItems" true})))
  (is (= ["foo" "bar" "bar"] (parse ["foo" "bar" "bar"]
                                    {"type"        "array"
                                     "items"       {"type" "string"}
                                     "uniqueItems" false})))
  (is (rejected (parse ["foo" "bar" "bar"]
                       {"type"        "array"
                        "items"       {"type" "string"}
                        "uniqueItems" true})))

  (is (empty? (parse []
                     {"type"        "array"
                      "items"       {"type" "string"}
                      "uniqueItems" true})))

  (is (= ["foo"] (parse ["foo"]
                        {"type"        "array"
                         "items"       {"type" "string"}
                         "uniqueItems" true}))))

(deftest object-values
  (is (= {:foo "bar"} (parse {:foo "bar"}
                             {"type"       "object"
                              "properties" {"foo" {"type" "string"}}})))

  (is (= {:foo (t/date-time 2015 4 28 10 56 12 98)}
         (parse {:foo "2015-04-28T12:56:12.098+02:00"}
                {"type"       "object"
                 "properties" {"foo" {"type"   "string"
                                      "format" "date-time"}}})))

  ; required
  (is (= {:foo "bar"} (parse {:foo "bar"}
                             {"type"       "object"
                              "properties" {"foo" {"type" "string"}}
                              "required"   ["foo"]})))
  (is (= {} (parse {}
                   {"type"       "object"
                    "properties" {"foo" {"type" "string"}}})))
  (is (rejected (parse {}
                       {"type"       "object"
                        "properties" {"foo" {"type" "string"}}
                        "required"   ["foo"]})))

  ; do not allow undefined keys by default
  (is (rejected (parse {:foo "bar"
                        :fiz "buz"}
                       {"type"       "object"
                        "properties" {"foo" {"type" "string"}}})))

  ; allow undefined keys if configured explicitly
  (is (= {:foo "bar"
          :fiz "buz"}
         (parse {:foo "bar"
                 :fiz "buz"}
                {"type"       "object"
                 "properties" {"foo" {"type" "string"}}}
                :allow-undefined-keys true))))

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
  (is (= {:bar "baz"}
         (p/extract-parameter-body
           {:uri     "/foo"
            :headers {"content-type" "application/json"}
            :swagger {:request {"consumes" ["application/json"]}}
            :body    (json-body
                       {:bar "baz"})}
           {"name" "testmap"}))))
