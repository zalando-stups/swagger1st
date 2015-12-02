(ns io.sarnowski.swagger1st.mapper-test
  (:require [clojure.test :refer :all]
            [io.sarnowski.swagger1st.mapper :as m]
            [io.sarnowski.swagger1st.core :as s1st]
            [schema.core :as s]))

; TODO tests for definition inheritance

;; path templating and matching

(deftest variable-keyword-extraction
  (is (= :foo (m/variable-to-keyword "{foo}")))
  (is (= "foo" (m/variable-to-keyword "foo"))))

(deftest path-splitting
  (is (= [] (m/split-path "/")))
  (is (= ["foo"] (m/split-path "/foo")))
  (is (= ["foo" "bar"] (m/split-path "/foo/bar"))))

(deftest path-matching
  (is (m/path-machtes? '() '()))
  (is (not (m/path-machtes? '() '("foo"))))
  (is (m/path-machtes? '("foo") '("foo")))
  (is (not (m/path-machtes? '("foo") '())))
  (is (m/path-machtes? '(:x) '("foo")))
  (is (m/path-machtes? '("foo" :x) '("foo" "bar")))
  (is (m/path-machtes? '("foo" :x "bar") '("foo" "baz" "bar")))
  (is (not (m/path-machtes? '("foo" :x "bar") '("fuzz" "baz" "bar")))))

(deftest create-request-tuple
  (is (= ["get" []] (first (m/create-request-tuple "get" {} "/" {}))))
  (is (= ["get" ["foo"]] (first (m/create-request-tuple "get" {} "/foo" {}))))
  (is (= ["get" ["foo" "bar"]] (first (m/create-request-tuple "get" {} "/foo/bar" {}))))
  (is (= ["get" ["foo" :bar "baz"]] (first (m/create-request-tuple "get" {} "/foo/{bar}/baz" {})))))

(deftest request-matches?
  (is (m/request-matches? ["get" []] {:request-method :get :uri "/"}))
  (is (m/request-matches? ["get" ["foo"]] {:request-method :get :uri "/foo"}))
  (is (m/request-matches? ["post" [:foo "bar"]] {:request-method :post :uri "/foo/bar"})))

;; definition to lookup structure

(deftest extract-requests
  (is (= #{["get" []]
           ["post" []]

           ["get" ["foo" :bar]]
           ["post" ["foo" :bar]]
           ["delete" ["foo" :bar]]}
         (->>
           (m/extract-requests
             {"paths" {
                       "/"
                       {"get"  {}
                        "post" {}}

                       "/foo/{bar}"
                       {"get"    {}
                        "post"   {}
                        "delete" {}}}})
           (map (fn [[k _]] k))
           (into #{})))))

(deftest honor-base-path-prefix
  (is (= #{["get" ["api"]]

           ["get" ["api" "foo" :bar]]}
         (->>
           (m/extract-requests
             {"basePath" "/api"
              "paths"    {
                          "/"
                          {"get" {}}

                          "/foo/{bar}"
                          {"get" {}}}})
           (map (fn [[k _]] k))
           (into #{})))))

(deftest lookup-request
  (let [requests {["get" []]              :get
                  ["post" []]             :post

                  ["get" ["foo" :bar]]    :get-foo
                  ["post" ["foo" :bar]]   :post-foo
                  ["delete" ["foo" :bar]] :delete-foo}]

    (is (= :get (second (m/lookup-request requests {:request-method :get :uri "/"}))))
    (is (= :get-foo (second (m/lookup-request requests {:request-method :get :uri "/foo/baz"}))))
    (is (= :delete-foo (second (m/lookup-request requests {:request-method :delete :uri "/foo/baz"}))))))

(deftest ref-denormalization
  (testing "implementation details"
    (is (=                      {"a" nil}
           (m/denormalize-refs* {"a" {"$ref" "#/b"}})))
    (is (=                      {"a" {"$ref" "#/a"}}
           (m/denormalize-refs* {"a" {"$ref" "#/a"}})))
    (is (=                      {"a" "foo"          "b" "foo"}
           (m/denormalize-refs* {"a" {"$ref" "#/b"} "b" "foo"})))
    (is (=                                 {"a" {"$ref" "#/b"} "b" "foo"}
           (m/denormalize-refs* (with-meta {"a" {"$ref" "#/b"} "b" "foo"}
                                           {:io.sarnowski.swagger1st.mapper/from "#/b"}))))
    (is (=                      {"a" "foo"          "b" "foo"          "c" "foo"}
           (m/denormalize-refs* {"a" {"$ref" "#/c"} "b" {"$ref" "#/c"} "c" "foo"}))))
  (testing "works"
    (is (=                     {"a" "foo"          "b" "foo"}
           (m/denormalize-refs {"a" {"$ref" "#/b"} "b" "foo"})))
    (is (=                     {"a" "foo"          "b" "foo"          "c" "foo"}
           (m/denormalize-refs {"a" {"$ref" "#/b"} "b" {"$ref" "#/c"} "c" "foo"}))))
  (testing "can survive circular refs"
    (is (=                     {"a" {"$ref" "#/a"}}
           (m/denormalize-refs {"a" {"$ref" "#/a"}})))
    (is (=                     {"a" {"$ref" "#/a"} "b" {"$ref" "#/b"}}
           (m/denormalize-refs {"a" {"$ref" "#/b"} "b" {"$ref" "#/a"}})))))

(s/defschema NotRef
  (s/pred #(not= "$ref" %)))

(s/defschema NoRefs
  (s/cond-pre {NotRef (s/recursive #'NoRefs)} [(s/recursive #'NoRefs)] s/Any))

(deftest semi-integration-smoke-check
  (testing "There should be no unresolved refs in the requests spec"
    (let [ctx (m/setup (s1st/context :yaml-cp (str "io/sarnowski/swagger1st/" "integration.yaml")))]
      (is (nil? (s/check NoRefs (:requests ctx)))))))
