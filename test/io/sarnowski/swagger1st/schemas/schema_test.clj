(ns io.sarnowski.swagger1st.schemas.schema-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [io.sarnowski.swagger1st.context :refer [load-swagger-definition]]
            [io.sarnowski.swagger1st.schemas.swagger-2-0 :as schema-2-0]))

; some sample definitions, all are valid

(deftest minimal-definition-edn
  (s/validate schema-2-0/root-object {"swagger" "2.0"
                                      "info"    {"title"   "minimal schema"
                                                 "version" "1.0"}
                                      "paths"   {"/test" {"get" {"operationId" "some-fn"
                                                                 "responses"   {200 {"description" "success"}}}}}}))

(deftest minimal-definition-yaml
  (s/validate schema-2-0/root-object
              (load-swagger-definition :yaml-cp "io/sarnowski/swagger1st/schemas/minimal.yaml")))

(deftest minimal-definition-json
  (s/validate schema-2-0/root-object
              (load-swagger-definition :json-cp "io/sarnowski/swagger1st/schemas/minimal.json")))

(deftest user-api-definition
  (s/validate schema-2-0/root-object
              (load-swagger-definition :yaml-cp "io/sarnowski/swagger1st/integration.yaml")))

(deftest default-definition
  (s/validate schema-2-0/root-object
              (load-swagger-definition :yaml-cp "io/sarnowski/swagger1st/schemas/default.yaml")))

(deftest simple-ref-definition
  (s/validate schema-2-0/root-object
              (load-swagger-definition :yaml-cp "io/sarnowski/swagger1st/schemas/simple-ref.yaml")))

(deftest kio-definition
  (s/validate schema-2-0/root-object
              (load-swagger-definition :yaml-cp "io/sarnowski/swagger1st/schemas/kio.yaml")))
