(ns io.sarnowski.swagger1st.validation
  (:require [schema.core :as schema]
            clojure.set
            [clojure.tools.logging :as log]
            [io.sarnowski.swagger1st.schemas.swagger-2-0 :as swagger-2-0]))

(defn collect-defined-params [definition]
  (set (keys (get definition "parameters"))))

(defn collect-used-params [definition]
  (let [paths (get definition "paths")]
    (->> paths
      vals
      (mapcat vals)
      (mapcat #(get % "parameters"))
      (mapcat vals)
      (map #(clojure.string/replace % "#/parameters/" ""))
      set)))

(defn validate [definition]
  (schema/validate swagger-2-0/root-object definition)
  (let [used-params (collect-used-params definition)
        defined-params (collect-defined-params definition)
        used-but-undefined-params (clojure.set/difference used-params defined-params)
        defined-but-unused-params (clojure.set/difference defined-params used-params)
        ]
    (if-not (empty? used-but-undefined-params)
      (throw (ex-info "Some parameters are used but not defined" {:names used-but-undefined-params})))
    (if-not (empty? defined-but-unused-params)
      ;; Not a critical error, a warning is enough
      (println "[swagger1st] [WARN] Params defined but not used: " defined-but-unused-params))))