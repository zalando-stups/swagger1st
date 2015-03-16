(defproject io.sarnowski/swagger1st "0.3.0"
  :description "A ring middleware that does routing based on a swagger definition."
  :url "https://github.com/sarnowski/swagger1st"

  :license {:name "ISC License"
            :url "http://opensource.org/licenses/ISC"}

  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]

                 [org.clojure/data.json "0.2.5"]
                 [circleci/clj-yaml "0.5.3"]

                 [prismatic/schema "0.3.7"]]

  :plugins [[lein-cloverage "1.0.2"]]

  :profiles {:dev {:dependencies [[ring/ring-mock "0.2.0"]
                                  [ring/ring-core "1.3.2"]
                                  [ring/ring-devel "1.3.2"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [org.slf4j/slf4j-api "1.7.7"]
                                  [org.slf4j/jul-to-slf4j "1.7.7"]
                                  [org.slf4j/jcl-over-slf4j "1.7.7"]
                                  [org.slf4j/slf4j-simple "1.7.7"]]}}

  :deploy-repositories [["releases" :clojars]]
  :signing {:gpg-key "tobias@sarnowski.io"})
