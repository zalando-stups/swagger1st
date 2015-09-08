# swagger1st (swagger first)

![Maven Central](https://img.shields.io/maven-central/v/io.sarnowski/swagger1st.svg)
[![Build Status](https://travis-ci.org/sarnowski/swagger1st.svg?branch=master)](https://travis-ci.org/sarnowski/swagger1st)
[![Coverage Status](https://coveralls.io/repos/sarnowski/swagger1st/badge.svg?branch=master)](https://coveralls.io/r/sarnowski/swagger1st?branch=master)

swagger1st is a Clojure [Ring](https://github.com/ring-clojure/ring) handler that parses, validates and routes requests
based on your [Swagger](http://swagger.io/) definition. Instead of defining routes and validation rules in your code,
you specify your API in the [Swagger 2.0 Specification format](https://github.com/swagger-api/swagger-spec) using
[their great tool set](http://editor.swagger.io/). This encourages an "API first" approach by letting you specify your API in a technology independent format. The resulting definition is the ultimate format for publishing, sharing and reviewing your API. swagger1st will use it as a configuration file for processing incoming requests. This approach makes sure, that your implementation and specification never gets out of sync. During
runtime, you can inspect and easily test your API with the built-in [Swagger UI](http://petstore.swagger.io/). You are also free to extend the interpretation of your definition according to your own needs.

## Kickstart

Use the following dependency in your [Leiningen](http://leiningen.org/) project:

    [io.sarnowski/swagger1st "<latest>"]

You find the latest version in [Maven central](http://repo1.maven.org/maven2/io/sarnowski/swagger1st/):

* ![Maven Central](https://img.shields.io/maven-central/v/io.sarnowski/swagger1st.svg)

The following setup creates a ring compliant handler.

```clojure
(ns example
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.security :as s1stsec]))

(def app
  (-> (s1st/context :yaml-cp definition)
      (s1st/discoverer)
      (s1st/mapper)
      (s1st/parser)
      (s1st/protector {"oauth2" (s1stsec/allow-all)})
      (s1st/executor)))
```

## Complete Example Projects

The [examples](examples/) directory contains some examples, that can help you bootstrap a complete setup:

* [Hello, World!](examples/helloworld/)
    * The simplest setup as a starting point for own applications. This already shows all parts necessary to combine in
      order to have a working API.
* [TODO](examples/todo/)
    * A TODO list application, showing integration with the lifecycle framework
      [component](https://github.com/stuartsierra/component). This integration allows all request handlers to access the
      defined dependencies like databases.
* [Friboo](https://github.com/zalando-stups/friboo)
    * Friboo is [Zalando](http://tech.zalando.com/)'s opinionated Clojure microservice library which uses swagger1st at
      its base for RESTful HTTP endpoints and also integrates with the
      [component](https://github.com/stuartsierra/component) framework. See the following projects who are real world
      applications of Zalando's cloud infrastructure based on Friboo with swagger1st:
        * [Kio](https://github.com/zalando-stups/kio)
        * [essentials](https://github.com/zalando-stups/essentials)
        * [TWINTIP](https://github.com/zalando-stups/twintip)
        * [mint](https://github.com/zalando-stups/mint)

## Compatibility Overview

swagger1st aims to implement all features of the Swagger 2.0 specification. Everything that you can define with Swagger
should be handled by swagger1st, so that you only have to write your business logic. Version 1.0 will implement all
elements (that makes sense to handle). Until then, the following document shows the current supported aspects of the
specification:

* [Swagger 2.0 Compatibility Document](comp-2.0.md)

## The Ring handler in detail

* `s1st/context` (required)
    * Creates a new context from a given definition. This context will be used by the next steps to prepare the execution
      of requests.
* `s1st/discoverer` (optional)
    * The discoverer enables certain HTTP endpoints, that makes it easy to work with your API. In particular, this enables
      the Swagger UI under the path `/ui/` and exposes the Swagger definition under `/swagger.json`.
* `s1st/mapper` (required)
    * The mapper denormalizes the given definition (e.g. resolves all `$ref`s) and figures out, which request definition
      maps to the actual incoming request. After this function, your `request` map contains the `:swagger` key, which
      contains a `:request` key containing the denormalized definition of the request and a `:key` key which can be used
      to uniquely identify a request.
* `s1st/parser` (required)
    * The parser parses the incoming request according to the definition and validates all inputs.
* `s1st/protector` (optional)
    * The protector can enforce all security definitions for you. As the security check implementations vary depending on
      your environment, this is only a framework to hook into the system and define callbacks for the actual checks.
* `s1st/executor` (required)
    * The executor executes your defined function in the end. At this point, the whole definition was validated and only
      valid requests make it up until here. You can also specify an own function resolver function in order to hook into
      your own framework.

## Development on swagger1st

Source code can be found on [GitHub](https://github.com/sarnowski/swagger1st). Read [this documentation](https://guides.github.com/introduction/flow/)
if you are just starting with GitHub. In addition, you need [Leiningen](http://leiningen.org/) as the build tool, make sure it works first.

The following commands are a kickstarter for development:

```shell
# get the source
$ git clone https://github.com/sarnowski/swagger1st.git
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

## License

Copyright (c) 2015, Tobias Sarnowski

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted,
provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
