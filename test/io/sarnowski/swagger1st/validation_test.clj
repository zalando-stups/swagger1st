(ns io.sarnowski.swagger1st.validation-test
  (:require [clojure.test :refer :all]
            [io.sarnowski.swagger1st.validation :refer [validate]]
            [io.sarnowski.swagger1st.context :refer [load-swagger-definition]]))


(deftest broken-ref
  (let [definition (load-swagger-definition :yaml-cp "io/sarnowski/swagger1st/schemas/broken-ref.yaml")]
    (try
     (do
      (validate definition)
      (is false "validation was expected to fail but didn't"))
     (catch Exception e
      (is (= (ex-data e) {:names #{"badParam"}}))))))


(deftest unused-parameter-definition
  (let [definition (load-swagger-definition :yaml-cp "io/sarnowski/swagger1st/schemas/unused-parameter-definition.yaml")]
    (is (=
      (with-out-str (validate definition)
      "[swagger1st] [WARN] Params defined but not used: #{unusedParam}\n")))))