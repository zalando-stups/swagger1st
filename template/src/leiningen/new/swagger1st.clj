(ns leiningen.new.swagger1st
  (:require [leiningen.new.templates :refer [renderer name-to-path ->files]]
            [leiningen.core.main :as main]))

(def render (renderer "swagger1st"))

(defn swagger1st
  "Creates a new swagger1st project."
  [name & features]
  (let [data {:name name
              :sanitized (name-to-path name)}
        features (into #{} features)]
    (main/info "Generating swagger1st project" name "...")
    (if (contains? features "+component")
      (->files data
        ["src/{{sanitized}}/api.clj" (render "component/api.clj" data)]
        ["resources/{{sanitized}}-api.yaml" (render "component/api.yaml" data)]
        ["README.md" (render "component/README.md" data)]
        ["project.clj" (render "component/project.clj" data)]
        ["src/{{sanitized}}/core.clj" (render "component/core.clj" data)]
        ["src/{{sanitized}}/http.clj" (render "component/http.clj" data)]
        ["src/{{sanitized}}/db.clj" (render "component/db.clj" data)])
      (->files data
        ["src/{{sanitized}}/api.clj" (render "simple/api.clj" data)]
        ["resources/{{sanitized}}-api.yaml" (render "simple/api.yaml" data)]
        ["README.md" (render "simple/README.md" data)]
        ["project.clj" (render "simple/project.clj" data)]
        ["src/{{sanitized}}/core.clj" (render "simple/core.clj" data)]))))
