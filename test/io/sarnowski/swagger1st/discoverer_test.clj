(ns io.sarnowski.swagger1st.discoverer-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [io.sarnowski.swagger1st.core-test :refer :all]
            [clojure.data.json :as json]))

(def app (create-app "discoverer_test.yaml"))

(deftest discovery

  (let [result (app (mock/request :get "/.well-known/schema-discovery"))
        discovery (json/read-str (:body result))]
    (is (= discovery
           {"schema_url"  "/swagger.json"
            "schema_type" "swagger-2.0"
            "ui_url"      "/ui/"})))

  (let [result (app (mock/request :get "/swagger.json"))
        definition (json/read-str (:body result))]
    (is (= "2.0" (get definition "swagger"))))

  (let [result (app (mock/request :get "/ui/"))
        html (:body result)]
    (is (.contains html "html")))

  (let [result (app (mock/request :get "/ui/swagger-ui.min.js"))
        js (slurp (:body result))]
    (is (.contains js "prototype"))))
