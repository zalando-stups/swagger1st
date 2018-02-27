## swagger1st: A Swagger-First Clojure Ring handler

![Maven Central](https://img.shields.io/maven-central/v/org.zalando/swagger1st.svg)
[![Build Status](https://travis-ci.org/zalando-stups/swagger1st.svg?branch=master)](https://travis-ci.org/zalando-stups/swagger1st)
[![codecov](https://codecov.io/gh/zalando-stups/swagger1st/branch/master/graph/badge.svg)](https://codecov.io/gh/zalando-stups/swagger1st)

swagger1st is a Clojure [Ring](https://github.com/ring-clojure/ring) handler that parses, validates and routes requests
based on your [Swagger](http://swagger.io/)/OpenAPI definition. It takes the opposite approach of [ring-swagger](https://github.com/metosin/ring-swagger)—which enables you to generate your Swagger spec from your Clojure code—by allowing you to use your Swagger spec to generate Clojure code.

Instead of defining routes and validation rules in your code, you can use swagger1st along with [Swagger/OpenAPI's great tool set](http://editor.swagger.io/) to specify your API according to the [Swagger/Open API 2.0 Specification](https://github.com/swagger-api/swagger-spec). This enables you to specify your API in an API-First, technology-independent format. The resulting definition is the ultimate format for publishing, sharing and reviewing your API.

#### Compatibility Overview
swagger1st aims to implement all of the Swagger/OpenAPI spec's features, so that you only have to write your business logic. [This document](https://github.com/zalando-stups/swagger1st/blob/master/comp-2.0.md) shows which aspects of the spec it currently supports.

swagger1st will use the Swagger definition of your API as a configuration file for processing incoming requests—ensuring that your implementation and specification always remain in sync. During runtime, you can inspect and easily test
your API with the built-in [Swagger UI](http://petstore.swagger.io/). You can also extend the interpretation of
your definition according to your own needs.

Imagine a simple API definition like this:

```yaml
swagger: '2.0'

info:
  title: Example API
  version: '0.1'

paths:
  /helloworld:
    get:
      summary: Returns a greeting.
      operationId: example.api/generate-greeting
      parameters:
        - name: firstname
          in: query
          type: string
          pattern: "^[A-Z][a-z]+"
      responses:
          200:
              description: say hello
```

By default, this definition is connected to your business logic via the `operationId`, which might be defined like so:

```clojure
(ns example.api
  (:require [ring.util.response :as r]))

(defn generate-greeting [request]
  (let [firstname (-> request :parameters :query :firstname)]
    (-> (r/response (str "Hello " firstname "!"))
        (r/content-type "plain/text"))))
```

This is all you need to do to define and implement your API. Only fully validated requests get to your function,
so you can rely on swagger1st to properly check all input parameters according to your definition. The function itself
is a normal Clojure function without any dependencies to swagger1st - simple as that.

### Quickstart

The following provides instructions for simple, complex and manual setups. For all three approaches you'll need to install [Leiningen](http://leiningen.org/) as the build tool.

#### Simple Setup
If you're bootstrapping a completely new project, or just want to try out swagger1st, you can use this Leiningen template:

```
$ lein new swagger1st myproject
$ cd myproject
$ lein ring server-headless
```

This will run a local web server on port 3000, so you can interact with the API at <http://localhost:3000/>. Also, you might want to have a look at <http://localhost:3000/ui/> for a graphical interface to explore and experiment with your API (using [Swagger UI](http://petstore.swagger.io/)).

### Complex Setup

To see how you can handle dependency injection with swagger1st, generate a project setup that includes Stuart Sierra's
[component](https://github.com/stuartsierra/component) framework:

```
$ lein new swagger1st myproject +component
$ cd myproject
$ lein run -m myproject.core
```

As with the simple setup above, this will launch a local web server on port 3000.

### Manual Setup

The following steps describe how to manually set up swagger1st in a Clojure project. This is especially useful if you want to integrate it into an existing project or cannot use the provided template for other reasons.

Use the following dependency in your [Leiningen](http://leiningen.org/) project:

    [org.zalando/swagger1st "<latest>"]

This creates a Ring-compliant handler:

```clojure
(ns example
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.security :as s1stsec]))

(def app
  (-> (s1st/context :yaml-cp "my-swagger-api.yaml")
      (s1st/discoverer)
      (s1st/mapper)
      (s1st/parser)
      (s1st/protector {"oauth2" (s1stsec/allow-all)})
      (s1st/executor)))
```

### Commands for Development

```shell
# get the source
$ git clone https://github.com/zalando-stups/swagger1st.git
$ cd swagger1st

# run the tests
$ lein test

# run all tests, including performance benchmarks
$ lein test :all

# build an own artifact for local development
$ lein install

# release a new version
$ lein release :minor
```

For interactive development, you can start a REPL by typing `lein repl`.

### Projects Using Swagger1st in Production

- [Friboo](https://github.com/zalando/friboo), a utility library for writing microservices in Clojure, with support for Swagger and OAuth. It uses swagger1st at its base for RESTful HTTP endpoints and also integrates with the [component](https://github.com/stuartsierra/component) framework.
- [STUPS.io](https://stups.io/) components [Kio](https://github.com/zalando-stups/kio), [PierOne](https://github.com/zalando-stups/pierone) (a complete Docker registry based on S3), [Essentials](https://github.com/zalando-stups/essentials), [TWINTIP](https://github.com/zalando-stups/twintip-storage) and [mint](https://github.com/zalando-stups/mint-storage)

### The Ring Handler in Detail

* `s1st/context` (required)
    * Creates a new context from a given definition. This context will be used by the next steps to prepare the
      execution of requests.
* `s1st/discoverer` (optional)
    * The discoverer enables certain HTTP endpoints, that makes it easy to work with your API. In particular, this
      enables the Swagger UI under the path `/ui/` and exposes the Swagger definition under `/swagger.json`.
* `s1st/mapper` (required)
    * The mapper denormalizes the given definition (e.g. resolves all `$ref`s) and figures out, which request definition
      maps to the actual incoming request. After this function, your `request` map contains the `:swagger` key, which
      contains a `:request` key containing the denormalized definition of the request and a `:key` key which can be used
      to uniquely identify a request.
* `s1st/parser` (required)
    * The parser parses the incoming request according to the definition and validates all inputs.
* `s1st/protector` (optional)
    * The protector can enforce all security definitions for you. As the security check implementations vary depending
      on your environment, this is only a framework to hook into the system and define callbacks for the actual checks.
* `s1st/executor` (required)
    * The executor executes your defined function in the end. At this point, the whole definition was validated and only
      valid requests make it up until here. You can also specify an own function resolver function in order to hook into
      your own framework.

### License

Copyright (c) 2015, Tobias Sarnowski
Copyright (c) 2016, Zalando SE

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted,
provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
