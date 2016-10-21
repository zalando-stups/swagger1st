(defproject org.zalando/swagger1st "0.22.0-beta3-SNAPSHOT"
  :description "A ring handler that does routing based on a swagger definition."
  :url "https://github.com/sarnowski/swagger1st"

  :license {:name "ISC License"
            :url "http://opensource.org/licenses/ISC"
            :distribution :repo}

  :scm {:url "git@github.com:sarnowski/swagger1st.git"}

  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]

                 [circleci/clj-yaml "0.5.5"]

                 [prismatic/schema "1.1.3"]

                 [clj-time "0.12.0"]

                 [ring-basic-authentication "1.0.5"]
                 [clj-http "3.3.0"]
                 [cheshire "5.6.3"]]

  :plugins [[lein-cloverage "1.0.6"]]

  :profiles {:dev {:dependencies [[ring/ring-core "1.5.0"]
                                  [ring/ring-devel "1.5.0"]
                                  [ring/ring-mock "0.3.0" :exclusions [ring/ring-codec]]
                                  [javax.servlet/servlet-api "2.5"]
                                  [org.slf4j/slf4j-api "1.7.21"]
                                  [org.slf4j/jul-to-slf4j "1.7.21"]
                                  [org.slf4j/jcl-over-slf4j "1.7.21"]
                                  [org.apache.logging.log4j/log4j-api "2.7"]
                                  [org.apache.logging.log4j/log4j-core "2.7"]
                                  [org.apache.logging.log4j/log4j-slf4j-impl "2.7"]
                                  [criterium "0.4.4"]
                                  [com.newrelic.agent.java/newrelic-api "3.32.0"]]}}

  :test-selectors {:default (complement :performance)
                   :performance :performance
                   :all (constantly true)}

  :pom-addition [:developers
                 [:developer
                  [:name "Tobias Sarnowski"]
                  [:url "http://www.sarnowski.io"]
                  [:email "tobias@sarnowski.io"]
                  [:timezone "+1"]]]

  :deploy-repositories {"releases" {:url "https://oss.sonatype.org/service/local/staging/deploy/maven2/" :creds :gpg}
                        "snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots/" :creds :gpg}})
