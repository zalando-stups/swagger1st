(ns io.sarnowski.swagger1st.validation-test
  (:require [clojure.test :refer :all]
            [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [response]]
            [ring.mock.request :as mock])
  (:import (clojure.lang ExceptionInfo)))

(def app
  (-> (s1st/context :yaml-cp "io/sarnowski/swagger1st/validation-test.yaml")
      (s1st/ring wrap-params)
      (s1st/mapper)
      (s1st/parser)
      (s1st/validator)
      (s1st/executor)))

(defn noop
  "Does nothing; used for swagger definition."
  [request] (response nil))

(defmacro status
  "Returns the status code of an http call or the http-code of an exception."
  [client-call]
  `(try
     (let [result# ~client-call]
       (:status result#))
     (catch ExceptionInfo e#
       (let [data# (ex-data e#)]
         (if (= 412 (:http-code data#))
           (:invalid-arguments data#)
           (:http-code data#))))))

;; TESTS

(deftest primitives

  ; no validation because no type hint in definition
  (is (= 200 (status (app (mock/request :get "/none/foobar")))))

  ; string works
  (is (= 200 (status (app (mock/request :get "/primitive/string/foobar")))))

  ; even if string contains number
  (is (= 200 (status (app (mock/request :get "/primitive/string/12345")))))

  ; int works
  (is (= 200 (status (app (mock/request :get "/primitive/int/12345")))))

  ; int does not allow string
  (is (= 400 (status (app (mock/request :get "/primitive/int/foobar"))))))

(deftest required

  ; only required parameters are fine
  (is (= 200 (status (app (mock/request :get "/required?required_unit=1")))))

  ; optionals are also fine
  (is (= 200 (status (app (mock/request :get "/required?required_unit=1&optional_unit=2")))))

  ; missing a required one is wrnog
  (is (= [{:name :required_unit, :in :query, :detail "(not (integer? nil))"}]
         (status (app (mock/request :get "/required"))))))
