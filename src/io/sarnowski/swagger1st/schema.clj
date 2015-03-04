(ns io.sarnowski.swagger1st.schema
  (:require [schema.core :as s]))

;;; Basic types

; TODO stricter type checking
(def s-integer s/Int)
(def s-long s/Int)
(def s-float s/Num)
(def s-double s/Num)
(def s-string s/Str)
(def s-byte s/Str)
(def s-boolean s/Bool)
(def s-date s/Str)
(def s-dateTime s/Str)

(defn extension? [^String k]
  (.startsWith "x-" k))

;;; complex types

(def swagger-contact
  {(s/optional-key "name")  s-string
   (s/optional-key "url")   s-string
   (s/optional-key "email") s-string})

(def swagger-license
  {(s/optional-key "name") s-string
   (s/optional-key "url")  s-string})

(def swagger-info
  {(s/required-key "title")             s-string
   (s/required-key "version")           s-string
   (s/optional-key "description")       s-string
   (s/optional-key "termsOfService")    s-string
   (s/optional-key "contact")           swagger-contact
   (s/optional-key "license")           swagger-license
   (s/optional-key (s/pred extension?)) s/Any})

(def swagger-external-docs
  {(s/required-key "url")         s-string
   (s/optional-key "description") s-string})

(def swagger-item
  {(s/optional-key "type")             s-string
   (s/optional-key "format")           s-string
   (s/optional-key "items")            (s/recursive #'swagger-item)
   (s/optional-key "collectionFormat") s-string
   (s/optional-key "default")          s/Any
   (s/optional-key "maximum")          s-long
   (s/optional-key "exclusiveMaximum") s-boolean
   (s/optional-key "maxLength")        s-long
   (s/optional-key "minLength")        s-long
   (s/optional-key "pattern")          s-string
   (s/optional-key "maxItems")         s-long
   (s/optional-key "minItems")         s-long
   (s/optional-key "uniqueItems")      s-boolean
   (s/optional-key "enum")             [s/Any]
   (s/optional-key "multipleOf")       s-long})

(def swagger-object
  {s/Any s/Any})

(def swagger-header
  (merge swagger-item
         {(s/optional-key "description") s-string}))

(def swagger-headers
  {s-string swagger-header})

(def swagger-examples
  {s-string s/Any})

(def swagger-response
  {(s/required-key "description") s-string
   (s/optional-key "schema")      swagger-object
   (s/optional-key "headers")     swagger-headers
   (s/optional-key "examples")    swagger-examples})

(def swagger-reference
  {(s/required-key "$ref") s-string})

(def swagger-parameter
  (merge swagger-item
         {(s/required-key "name")        s-string
          (s/required-key "in")          s-string
          (s/optional-key "description") s-string
          (s/optional-key "required")    s-boolean

          (s/optional-key "schema")      swagger-object}))

(def swagger-parameters
  {s-string swagger-parameter})

(def swagger-responses
  {s/Any (s/either swagger-response swagger-reference)})

(def swagger-security
  {})

(def swagger-operation
  {(s/required-key "operationId")       s-string
   (s/required-key "responses")         swagger-responses
   (s/optional-key "tags")              [s-string]
   (s/optional-key "summary")           s-string
   (s/optional-key "description")       s-string
   (s/optional-key "externalDocs")      swagger-external-docs
   (s/optional-key "consumes")          [s-string]
   (s/optional-key "produces")          [s-string]
   (s/optional-key "parameters")        swagger-parameters  ; TODO (s/either swagger-parameters swagger-reference)
   (s/optional-key "schemes")           [s-string]
   (s/optional-key "deprecated")        s-boolean
   (s/optional-key "security")          swagger-security
   (s/optional-key (s/pred extension?)) s/Any})

(def swagger-path
  {(s/optional-key "$ref")              s-string
   (s/optional-key "get")               swagger-operation
   (s/optional-key "put")               swagger-operation
   (s/optional-key "post")              swagger-operation
   (s/optional-key "delete")            swagger-operation
   (s/optional-key "options")           swagger-operation
   (s/optional-key "head")              swagger-operation
   (s/optional-key "patch")             swagger-operation
   (s/optional-key "parameters")        (s/either swagger-parameters swagger-reference)
   (s/optional-key (s/pred extension?)) s/Any})

(def swagger-paths
  {s-string                             swagger-path
   (s/optional-key (s/pred extension?)) s/Any})

(def swagger-definitions
  {})

(def swagger-security-definition
  {})

(def swagger-tag
  {(s/required-key "name")              s-string
   (s/optional-key "description")       s-string
   (s/optional-key "externalDocs")      swagger-external-docs
   (s/optional-key (s/pred extension?)) s/Any})

(def swagger-schema
  {(s/required-key "swagger")             (s/eq "2.0")
   (s/required-key "info")                swagger-info
   (s/required-key "paths")               swagger-paths
   (s/optional-key "host")                s-string
   (s/optional-key "basePath")            s-string
   (s/optional-key "schemes")             [s-string]
   (s/optional-key "consumes")            [s-string]
   (s/optional-key "produces")            [s-string]
   (s/optional-key "definitions")         swagger-definitions
   (s/optional-key "parameters")          swagger-parameters
   (s/optional-key "responses")           swagger-responses
   (s/optional-key "securityDefinitions") swagger-security-definition
   (s/optional-key "security")            swagger-security
   (s/optional-key "tags")                [swagger-tag]
   (s/optional-key "externalDocs")        swagger-external-docs})
