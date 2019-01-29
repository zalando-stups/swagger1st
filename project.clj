(defproject org.zalando/swagger1st "0.26.0-SNAPSHOT"
  :description "A ring handler that does routing based on a swagger definition."
  :url "https://github.com/zalando/swagger1st"

  :license {:name "ISC License"
            :url "http://opensource.org/licenses/ISC"
            :distribution :repo}

  :scm {:url "git@github.com:zalando/swagger1st.git"}

  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.4.0"]

                 [circleci/clj-yaml "0.5.5"]

                 [prismatic/schema "1.1.6"]

                 [clj-time "0.13.0"]

                 [ring-basic-authentication "1.0.5"]
                 [clj-http "3.6.1"]
                 [cheshire "5.7.1"]]

  :plugins [[lein-cloverage "1.0.9"]]

  :profiles {:dev {:dependencies [[ring/ring-core "1.6.3"]
                                  [ring/ring-devel "1.6.3"]
                                  [ring/ring-mock "0.3.1"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [org.slf4j/slf4j-api "1.7.25"]
                                  [org.slf4j/jul-to-slf4j "1.7.25"]
                                  [org.slf4j/jcl-over-slf4j "1.7.25"]
                                  [org.apache.logging.log4j/log4j-api "2.8.2"]
                                  [org.apache.logging.log4j/log4j-core "2.8.2"]
                                  [org.apache.logging.log4j/log4j-slf4j-impl "2.8.2"]
                                  [whitepages/expect-call "0.1.0"]
                                  [criterium "0.4.4"]
                                  [com.newrelic.agent.java/newrelic-api "3.40.0"]]}}

  :test-selectors {:default (complement :performance)
                   :performance :performance
                   :all (constantly true)}

  :pom-addition [:developers
                 [:developer
                  [:name "Tobias Sarnowski"]
                  [:url "http://www.sarnowski.io"]
                  [:email "tobias@sarnowski.io"]
                  [:timezone "+1"]]
                 [:developer
                  [:name "Andre Hartmann"]
                  [:email "andre.hartmann@zalando.de"]
                  [:timezone "+1"]]]

  :deploy-repositories {"releases" {:url "https://oss.sonatype.org/service/local/staging/deploy/maven2/" :creds :gpg}
                        "snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots/" :creds :gpg}})
