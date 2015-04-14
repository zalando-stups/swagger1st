# swagger1st (swagger first)

A Clojure [Ring](https://github.com/ring-clojure/ring) middleware that does routing, serialization and validation based
on [swagger](http://swagger.io/) definitions.

Currently only supports Swagger 2.0 specification.

[![Build Status](https://travis-ci.org/sarnowski/swagger1st.svg?branch=master)](https://travis-ci.org/sarnowski/swagger1st)
[![Coverage Status](https://coveralls.io/repos/sarnowski/swagger1st/badge.svg?branch=master)](https://coveralls.io/r/sarnowski/swagger1st?branch=master)

## Usage

Use the following dependency:

    [io.sarnowski/swagger1st <latest>]

from Maven central.

Add the swagger1st middleware into your ring handler chain and specify the schema location.

```clojure
(ns example
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.security :as s1stsec]
            [ring.middleware.params :refer [wrap-params]]))

(def app
  (-> (s1st/swagger-context ::s1st/yaml-cp "example.yaml")
      (s1st/swagger-ring wrap-params)
      (s1st/swagger-mapper)
      (s1st/swagger-discovery)
      (s1st/swagger-parser)
      (s1st/swagger-validator)
      (s1st/swagger-security {"oauth2_def" (s1stsec/allow-all)
                              "userpw_def" (s1stsec/allow-all)})
      (s1st/swagger-executor)))
```

## Examples

The [examples](examples/) directory contains the following examples:

* [Hello, World!](examples/helloworld/)
    * The simplest setup as a starting point for own applications.
* [TODO](examples/todo/)
    * A TODO list application, showing integration with the lifecycle framework [component](https://github.com/stuartsierra/component).

## Middlewares in detail

The here described middlewares should also be run in the same order. They are intentionally split up in order to plug
in other middlewares in between the process (or to disable certain features).

### swagger-mapper

The mapper parses the given definition and maps incoming request to a defined operation. After this middleware, the
request carries the `:swagger` key with the original swagger definition and the `:swagger-request` key if the request
matched a defined operation. The `:swagger-request` contains the denormalized definition of this operation.

In order to load the swagger definition, you have to specify the source type:

* **direct** - swagger definition already as clojure data structures
* **json** - a string containing a JSON formatted swagger definition
* **json-file** - a reference to a file, containing JSON
* **json-cp** - a reference to a classpath resource, containing JSON
* **yaml** - a string containing a YAML formatted swagger definition
* **yaml-file** - a reference to a file, containing YAML
* **yaml-cp** - a reference to a classpath resource, containing YAML

Optional arguments:

* `:surpress-favicon` if `/favicon.ico` requests should be ignored, enabled by default
* `:cors-origin` disabled by default, can be set to a domain like '*.zalando.de' and leads to the
  [CORS headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS) being set.


```clojure
(io.sarnowski.swagger1st.core/swagger-mapper [definition-type] [definition-source] [optional-arguments])
```

### swagger-discovery

The discovery middleware exposes a standard endpoint `/.discovery` which provides a JSON document, containing URLs to
the swagger definition and the UI:

```json
{
    "definition": "/swagger.json",
    "ui": "/ui/"
}
```

```clojure
(io.sarnowski.swagger1st.core/swagger-discovery [:discovery "/.discovery"]
                                                [:definition "/swagger.json"]
                                                [:ui "/ui/"])
```

### swagger-parser

The parser extracts the defined parameters from its different sources and deserializes bodies if necessary. It requires
the `wrap-params` middleware to be run beforehand in order to correctly parse parameters. After the parsing, the
`:parameters` key will be attached to the request, containing a map of all possible parameter types (`in` definitions)
which them self contain the parameters as keywords of their names. For example:

```clojure
{
  ; standard ring request including :swagger and :swagger-request keys
  :parameters {
    :path {
      :name "userId"
    }
    :query {
      :page 1
      :pageSize 20
    }
  }
}
```

```clojure
(io.sarnowski.swagger1st.core/swagger-parser)
```

### swagger-validator

The validator middleware will validate all parsed parameters according to their definition.

TODO not implemented yet!

```clojure
(io.sarnowski.swagger1st.core/swagger-validator)
```

### swagger-security

The security middleware will enforce defined security restrictions and handle OAuth 2.0 flows.

TODO not implemented yet!

```clojure
(io.sarnowski.swagger1st.core/swagger-security)
```

### swagger-executor

The executor is not a middleware but the final handler that uses the operation definition's `operationId` to match the
function to be called. The default implementation matches the operationId directly as a clojure function. It is possible
to configure an own resolver function. Resolver function must take one parameter, they will get the operationId in and
must return a function to-be-called or nil if resolution wasn't possible. (Hint: this is also fulfilled by a map) It is
possible to chain multiple handlers for fallbacks.

```clojure
(s1st/swagger-executor :mappers [

   ; manual mapping of operationIds to functions
   {"my-operation-id-1" my-special-function
    "my-operation-id-2" my-very-special-function}

   ; fallback directly resolve function
   s1st/resolve-function

   ])
```

```clojure
(io.sarnowski.swagger1st.core/swagger-executor [:mappers [list of resolver-functions]])
```

## License

Copyright (c) 2015, Tobias Sarnowski

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted,
provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
