(ns io.sarnowski.swagger1st.schemas.swagger-2-0
  (:require [schema.core :as s]))

;;; Basic types

; TODO stricter type checking
(def integer s/Int)
(def long s/Int)
(def float s/Num)
(def double s/Num)
(def string s/Str)
(def byte s/Str)
(def boolean s/Bool)
(def date s/Str)
(def dateTime s/Str)

(defn extension? [^String k]
  (.startsWith "x-" k))

;;; complex types

(def contact-object
  {(s/optional-key "name")  string
   (s/optional-key "url")   string
   (s/optional-key "email") string})

(def license-object
  {(s/optional-key "name") string
   (s/optional-key "url")  string})

(def info-object
  {(s/required-key "title")             string
   (s/required-key "version")           string
   (s/optional-key "description")       string
   (s/optional-key "termsOfService")    string
   (s/optional-key "contact")           contact-object
   (s/optional-key "license")           license-object
   (s/optional-key (s/pred extension?)) s/Any})

(def external-documentation-object
  {(s/required-key "url")         string
   (s/optional-key "description") string})

(def reference-object
  {(s/required-key "$ref") string})

(def items-object
  {(s/required-key "type")             string
   (s/optional-key "format")           string
   (s/optional-key "items")            (s/recursive #'items-object)
   (s/optional-key "collectionFormat") string
   (s/optional-key "default")          s/Any
   (s/optional-key "maximum")          long
   (s/optional-key "exclusiveMaximum") boolean
   (s/optional-key "minimum")          long
   (s/optional-key "exclusiveMinimum") boolean
   (s/optional-key "maxLength")        long
   (s/optional-key "minLength")        long
   (s/optional-key "pattern")          string
   (s/optional-key "maxItems")         long
   (s/optional-key "minItems")         long
   (s/optional-key "uniqueItems")      boolean
   (s/optional-key "enum")             [s/Any]
   (s/optional-key "multipleOf")       long})

(def xml-object
  {(s/optional-key "name")      string
   (s/optional-key "namespace") string
   (s/optional-key "prefix")    string
   (s/optional-key "attribute") boolean
   (s/optional-key "wraped")    boolean})

(def schema-object
  {(s/optional-key "type")             string
   (s/optional-key "format")           string
   (s/optional-key "items")            (s/recursive #'schema-object)
   (s/optional-key "collectionFormat") string
   (s/optional-key "default")          s/Any
   (s/optional-key "maximum")          long
   (s/optional-key "exclusiveMaximum") boolean
   (s/optional-key "minimum")          long
   (s/optional-key "exclusiveMinimum") boolean
   (s/optional-key "maxLength")        long
   (s/optional-key "minLength")        long
   (s/optional-key "pattern")          string
   (s/optional-key "maxItems")         long
   (s/optional-key "minItems")         long
   (s/optional-key "uniqueItems")      boolean
   (s/optional-key "enum")             [s/Any]
   (s/optional-key "multipleOf")       long
   (s/optional-key "title")            string
   (s/optional-key "description")      string
   (s/optional-key "maxProperties")    long
   (s/optional-key "minProperties")    long
   (s/optional-key "required")         [string]
   (s/optional-key "allOf")            (s/recursive #'schema-object)
   (s/optional-key "properties")       {string (s/recursive #'schema-object)}
   (s/optional-key "discriminator")    string
   (s/optional-key "readOnly")         boolean
   (s/optional-key "xml")              xml-object
   (s/optional-key "externalDocs")     external-documentation-object
   (s/optional-key "example")          s/Any
   (s/optional-key "$ref")             string})

(def header-object
  (merge
    items-object
    {(s/optional-key "description") string}))

(def headers-object
  {string header-object})

(def example-object
  {string s/Any})

(def response-object
  {(s/required-key "description") string
   (s/optional-key "schema")      schema-object
   (s/optional-key "headers")     headers-object
   (s/optional-key "examples")    example-object})

(def parameter-object
  (merge
    items-object
    {(s/required-key "name")              string
     (s/required-key "in")                string
     (s/optional-key "description")       string
     (s/optional-key "required")          boolean
     (s/optional-key "schema")            schema-object
     (s/optional-key (s/pred extension?)) s/Any}))

(def parameters-definitions-object
  {string parameter-object})

(def responses-definitions-object
  {string response-object})

(def security-requirement-object
  {string [string]})

(def responses-object
  {(s/optional-key "default")           (s/either response-object reference-object)
   (s/either long string)               (s/either response-object reference-object)
   (s/optional-key (s/pred extension?)) s/Any})

(def operation-object
  {(s/required-key "operationId")       string
   (s/required-key "responses")         responses-object
   (s/optional-key "tags")              [string]
   (s/optional-key "summary")           string
   (s/optional-key "description")       string
   (s/optional-key "externalDocs")      external-documentation-object
   (s/optional-key "consumes")          [string]
   (s/optional-key "produces")          [string]
   (s/optional-key "parameters")        [(s/either parameter-object reference-object)]
   (s/optional-key "schemes")           [string]
   (s/optional-key "deprecated")        boolean
   (s/optional-key "security")          security-requirement-object
   (s/optional-key (s/pred extension?)) s/Any})

(def path-object
  {(s/optional-key "$ref")              string
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
  {string                               path-object
   (s/optional-key (s/pred extension?)) s/Any})

(def definitions-object
  {string schema-object})

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
  {string string})

(def security-scheme-object
  {(s/required-key "type")              security-scheme-types
   (s/optional-key "description")       string
   (s/optional-key "name")              string
   (s/optional-key "in")                string
   (s/optional-key "flow")              security-scheme-oauth2-flows
   (s/optional-key "authorizationUrl")  string
   (s/optional-key "tokenUrl")          string
   (s/optional-key "scopes")            scopes-object
   (s/optional-key (s/pred extension?)) s/Any})

(def security-definitions-object
  {string security-scheme-object})

(def tag-object
  {(s/required-key "name")              string
   (s/optional-key "description")       string
   (s/optional-key "externalDocs")      external-documentation-object
   (s/optional-key (s/pred extension?)) s/Any})

(def root-object
  {(s/required-key "swagger")             (s/eq "2.0")
   (s/required-key "info")                info-object
   (s/optional-key "host")                string
   (s/optional-key "basePath")            string
   (s/optional-key "schemes")             [string]
   (s/optional-key "consumes")            [string]
   (s/optional-key "produces")            [string]
   (s/required-key "paths")               paths-object
   (s/optional-key "definitions")         definitions-object
   (s/optional-key "parameters")          parameters-definitions-object
   (s/optional-key "responses")           responses-definitions-object
   (s/optional-key "securityDefinitions") security-definitions-object
   (s/optional-key "security")            [security-requirement-object]
   (s/optional-key "tags")                [tag-object]
   (s/optional-key "externalDocs")        external-documentation-object})
