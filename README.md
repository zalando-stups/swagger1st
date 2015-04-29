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

The following setup creates a ring compliant handler.

```clojure
(ns example
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.executor :as s1stexec]
            [io.sarnowski.swagger1st.util.api :as s1stapi]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [ring.middleware.params :refer [wrap-params]]))

(def app
  (-> (s1st/context :yaml-cp definition)
      (s1st/ring s1stapi/add-hsts-header)
      (s1st/ring s1stapi/add-cors-headers)
      (s1st/ring s1stapi/surpress-favicon-requests)
      (s1st/discoverer)
      (s1st/ring wrap-params)
      (s1st/mapper)
      (s1st/parser)
      (s1st/protector {"oauth2" (s1stsec/allow-all)})
      (s1st/executor)))
```

## Examples

The [examples](examples/) directory contains the following examples:

* [Hello, World!](examples/helloworld/)
    * The simplest setup as a starting point for own applications.
* [TODO](examples/todo/)
    * A TODO list application, showing integration with the lifecycle framework [component](https://github.com/stuartsierra/component).
* [Friboo](https://github.com/zalando-stups/friboo)
    * Friboo is Zalando's opinionated Clojure microservice library which uses swagger1st at its base and als integrates
      with the [component](https://github.com/stuartsierra/component) framework. See the following project who also use
      Friboo with swagger1st:
        * [Kio](https://github.com/zalando-stups/kio)
        * [TWINTIP](https://github.com/zalando-stups/twintip)
        * [mint](https://github.com/zalando-stups/mint)

## Middlewares in detail

TODO write new detailed documentation, old one is invalid after refactoring.

## License

Copyright (c) 2015, Tobias Sarnowski

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted,
provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
