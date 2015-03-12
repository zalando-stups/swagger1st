(ns io.sarnowski.swagger1st.schema-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [io.sarnowski.swagger1st.core :as core]
            [io.sarnowski.swagger1st.schemas.swagger-2-0 :as schema-2-0]))

(deftest minimal-definition-edn
  (s/validate schema-2-0/root-object {"swagger" "2.0"
                                      "info"    {"title"   "minimal schema"
                                                 "version" "1.0"}
                                      "paths"   {"/test" {"get" {"operationId" "some-fn"
                                                                 "responses"   {200 {"description" "success"}}}}}}))

(deftest minimal-definition-yaml
  (s/validate schema-2-0/root-object
              (core/load-swagger-definition ::core/yaml-cp "io/sarnowski/swagger1st/minimal.yaml")))

(deftest minimal-definition-json
  (s/validate schema-2-0/root-object
              (core/load-swagger-definition ::core/json-cp "io/sarnowski/swagger1st/minimal.json")))

(deftest user-api-definition
  (s/validate schema-2-0/root-object
              (core/load-swagger-definition ::core/yaml-cp "io/sarnowski/swagger1st/user-api.yaml")))

(deftest default-definition
  (s/validate schema-2-0/root-object
              (core/load-swagger-definition ::core/yaml-cp "io/sarnowski/swagger1st/default.yaml")))

(deftest simple-ref-definition
  (s/validate schema-2-0/root-object
              (core/load-swagger-definition ::core/yaml-cp "io/sarnowski/swagger1st/simple-ref.yaml")))
