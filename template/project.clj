(defproject swagger1st/lein-template "0.1.0-SNAPSHOT"
  :description "Leiningen template for creating a swagger1st app."
  :url "https://github.com/sarnowski/swagger1st"

  :license {:name "ISC License"
            :url "http://opensource.org/licenses/ISC"
            :distribution :repo}

  :eval-in-leiningen true

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "template-"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
