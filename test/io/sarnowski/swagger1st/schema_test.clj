(ns io.sarnowski.swagger1st.schema-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [io.sarnowski.swagger1st.schema :as s1st]))

(deftest minimal-definition
  (s/validate s1st/swagger-schema {"swagger" "2.0"
                                   "info" {"title" "minimal schema"
                                          "version" "1.0"}
                                   "paths" {"/test" {"get" {"operationId" "some-fn"
                                                            "responses" {"200" {"description" "success"}}}}}}))
