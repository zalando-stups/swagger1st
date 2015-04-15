(ns io.sarnowski.swagger1st.schemas.swagger-2-0
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

(def primitives
  {"integer" s-integer
   "long" s-long
   "float" s-float
   "double" s-double
   "string" s-string
   "byte" s-byte
   "boolean" s-boolean
   "date" s-date
   "dateTime" s-dateTime})

;;; complex types

(def contact-object
  {(s/optional-key "name")  s-string
   (s/optional-key "url")   s-string
   (s/optional-key "email") s-string})

(def license-object
  {(s/optional-key "name") s-string
   (s/optional-key "url")  s-string})

(def info-object
  {(s/required-key "title")             s-string
   (s/required-key "version")           s-string
   (s/optional-key "description")       s-string
   (s/optional-key "termsOfService")    s-string
   (s/optional-key "contact")           contact-object
   (s/optional-key "license")           license-object
   (s/optional-key (s/pred extension?)) s/Any})

(def external-documentation-object
  {(s/required-key "url")         s-string
   (s/optional-key "description") s-string})

(def reference-object
  {(s/required-key "$ref") s-string})

(def items-object
  {(s/optional-key "type")             s-string
   (s/optional-key "format")           s-string
   (s/optional-key "items")            (s/recursive #'items-object)
   (s/optional-key "collectionFormat") s-string
   (s/optional-key "default")          s/Any
   (s/optional-key "maximum")          s-long
   (s/optional-key "exclusiveMaximum") s-boolean
   (s/optional-key "minimum")          s-long
   (s/optional-key "exclusiveMinimum") s-boolean
   (s/optional-key "maxLength")        s-long
   (s/optional-key "minLength")        s-long
   (s/optional-key "pattern")          s-string
   (s/optional-key "maxItems")         s-long
   (s/optional-key "minItems")         s-long
   (s/optional-key "uniqueItems")      s-boolean
   (s/optional-key "enum")             [s/Any]
   (s/optional-key "multipleOf")       s-long})

(def xml-object
  {(s/optional-key "name")      s-string
   (s/optional-key "namespace") s-string
   (s/optional-key "prefix")    s-string
   (s/optional-key "attribute") s-boolean
   (s/optional-key "wraped")    s-boolean})

(def schema-object
  {(s/optional-key "type")             s-string
   (s/optional-key "format")           s-string
   (s/optional-key "items")            (s/recursive #'schema-object)
   (s/optional-key "collectionFormat") s-string
   (s/optional-key "default")          s/Any
   (s/optional-key "maximum")          s-long
   (s/optional-key "exclusiveMaximum") s-boolean
   (s/optional-key "minimum")          s-long
   (s/optional-key "exclusiveMinimum") s-boolean
   (s/optional-key "maxLength")        s-long
   (s/optional-key "minLength")        s-long
   (s/optional-key "pattern")          s-string
   (s/optional-key "maxItems")         s-long
   (s/optional-key "minItems")         s-long
   (s/optional-key "uniqueItems")      s-boolean
   (s/optional-key "enum")             [s/Any]
   (s/optional-key "multipleOf")       s-long
   (s/optional-key "title")            s-string
   (s/optional-key "description")      s-string
   (s/optional-key "maxProperties")    s-long
   (s/optional-key "minProperties")    s-long
   (s/optional-key "required")         [s-string]
   (s/optional-key "allOf")            (s/recursive #'schema-object)
   (s/optional-key "properties")       {s-string (s/recursive #'schema-object)}
   (s/optional-key "discriminator")    s-string
   (s/optional-key "readOnly")         s-boolean
   (s/optional-key "xml")              xml-object
   (s/optional-key "externalDocs")     external-documentation-object
   (s/optional-key "example")          s/Any
   (s/optional-key "$ref")             s-string})

(def header-object
  (merge
    items-object
    {(s/optional-key "description") s-string}))

(def headers-object
  {s-string header-object})

(def example-object
  {s-string s/Any})

(def response-object
  {(s/required-key "description") s-string
   (s/optional-key "schema")      schema-object
   (s/optional-key "headers")     headers-object
   (s/optional-key "examples")    example-object})

(def parameter-object
  (merge
    items-object
    {(s/required-key "name")              s-string
     (s/required-key "in")                s-string
     (s/optional-key "description")       s-string
     (s/optional-key "required")          s-boolean
     (s/optional-key "schema")            schema-object
     (s/optional-key (s/pred extension?)) s/Any}))

(def parameters-definitions-object
  {s-string parameter-object})

(def responses-definitions-object
  {s-string response-object})

(def security-requirement-object
  {s-string [s-string]})

(def responses-object
  {(s/optional-key "default")           (s/either response-object reference-object)
   (s/either s-long s-string)               (s/either response-object reference-object)
   (s/optional-key (s/pred extension?)) s/Any})

(def operation-object
  {(s/required-key "operationId")       s-string
   (s/required-key "responses")         responses-object
   (s/optional-key "tags")              [s-string]
   (s/optional-key "summary")           s-string
   (s/optional-key "description")       s-string
   (s/optional-key "externalDocs")      external-documentation-object
   (s/optional-key "consumes")          [s-string]
   (s/optional-key "produces")          [s-string]
   (s/optional-key "parameters")        [(s/either parameter-object reference-object)]
   (s/optional-key "schemes")           [s-string]
   (s/optional-key "deprecated")        s-boolean
   (s/optional-key "security")          [security-requirement-object]
   (s/optional-key (s/pred extension?)) s/Any})

(def path-object
  {(s/optional-key "$ref")              s-string
   (s/optional-key "get")               operation-object
   (s/optional-key "put")               operation-object
   (s/optional-key "post")              operation-object
   (s/optional-key "delete")            operation-object
   (s/optional-key "options")           operation-object
   (s/optional-key "head")              operation-object
   (s/optional-key "patch")             operation-object
   (s/optional-key "parameters")        [(s/either parameter-object reference-object)]
   (s/optional-key (s/pred extension?)) s/Any})

(def paths-object
  {s-string                               path-object
   (s/optional-key (s/pred extension?)) s/Any})

(def definitions-object
  {s-string schema-object})

(def security-scheme-types
  (s/enum "basic"
          "apiKey"
          "oauth2"))

(def security-scheme-oauth2-flows
  (s/enum "implicit"
          "password"
          "application"
          "accessCode"))

(def scopes-object
  {s-string s-string})

(def security-scheme-object
  {(s/required-key "type")              security-scheme-types
   (s/optional-key "description")       s-string
   (s/optional-key "name")              s-string
   (s/optional-key "in")                s-string
   (s/optional-key "flow")              security-scheme-oauth2-flows
   (s/optional-key "authorizationUrl")  s-string
   (s/optional-key "tokenUrl")          s-string
   (s/optional-key "scopes")            scopes-object
   (s/optional-key (s/pred extension?)) s/Any})

(def security-definitions-object
  {s-string security-scheme-object})

(def tag-object
  {(s/required-key "name")              s-string
   (s/optional-key "description")       s-string
   (s/optional-key "externalDocs")      external-documentation-object
   (s/optional-key (s/pred extension?)) s/Any})

(def root-object
  {(s/required-key "swagger")             (s/eq "2.0")
   (s/required-key "info")                info-object
   (s/optional-key "host")                s-string
   (s/optional-key "basePath")            s-string
   (s/optional-key "schemes")             [s-string]
   (s/optional-key "consumes")            [s-string]
   (s/optional-key "produces")            [s-string]
   (s/required-key "paths")               paths-object
   (s/optional-key "definitions")         definitions-object
   (s/optional-key "parameters")          parameters-definitions-object
   (s/optional-key "responses")           responses-definitions-object
   (s/optional-key "securityDefinitions") security-definitions-object
   (s/optional-key "security")            [security-requirement-object]
   (s/optional-key "tags")                [tag-object]
   (s/optional-key "externalDocs")        external-documentation-object})
