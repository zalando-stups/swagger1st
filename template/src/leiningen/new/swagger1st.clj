(ns leiningen.new.swagger1st
  (:require [leiningen.new.templates :refer [renderer name-to-path ->files]]
            [leiningen.core.main :as main]))

(def render (renderer "swagger1st"))

(defn swagger1st
  "Creates a new swagger1st project."
  [name]
  (let [data {:name name
              :sanitized (name-to-path name)}]
    (main/info "Generating fresh 'lein new' swagger1st project.")
    (->files data
             ["README.md" (render "README.md" data)]
             ["project.clj" (render "project.clj" data)]
             ["src/{{sanitized}}/api.clj" (render "api.clj" data)]
             ["src/{{sanitized}}/core.clj" (render "core.clj" data)]
             ["resources/{{sanitized}}-api.yaml" (render "api.yaml" data)])))
