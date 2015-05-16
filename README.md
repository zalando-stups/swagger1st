# swagger1st (swagger first)

![Maven Central](https://img.shields.io/maven-central/v/io.sarnowski/swagger1st.svg)
[![Build Status](https://travis-ci.org/sarnowski/swagger1st.svg?branch=master)](https://travis-ci.org/sarnowski/swagger1st)
[![Coverage Status](https://coveralls.io/repos/sarnowski/swagger1st/badge.svg?branch=master)](https://coveralls.io/r/sarnowski/swagger1st?branch=master)

swagger1st is a Clojure [Ring](https://github.com/ring-clojure/ring) handler that routes, parses and validates requests
based on your [Swagger](http://swagger.io/) definition. Instead of defining routes and validation rules in your code,
you specify your API in the [Swagger 2.0 Specification format](https://github.com/swagger-api/swagger-spec) using
[their great tool set](http://editor.swagger.io/). The resulting definition is the ultimate format for documenting,
sharing and reviewing your API. swagger1st will use it as a configuration file for processing incoming requests. During
runtime, you can inspect and easily test your API with the built-in [Swagger UI](http://petstore.swagger.io/).

## Kickstart

Use the following dependency in your [Leiningen](http://leiningen.org/) project:

    [io.sarnowski/swagger1st "<latest>"]

You find the latest version in [Maven central](http://repo1.maven.org/maven2/io/sarnowski/swagger1st/):

* ![Maven Central](https://img.shields.io/maven-central/v/io.sarnowski/swagger1st.svg)

The following setup creates a ring compliant handler.

```clojure
(ns example
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [ring.middleware.params :refer [wrap-params]]))

(def app
  (-> (s1st/context :yaml-cp definition)
      (s1st/discoverer)
      (s1st/ring wrap-params)
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

TODO explain different chain handlers, what they output, where you might want to hook into

## Development on swagger1st



TODO link to github, git clone, running tests, pull requests

## License

Copyright (c) 2015, Tobias Sarnowski

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted,
provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
