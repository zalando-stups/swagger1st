# swagger1st (swagger first)

A Clojure [Ring](https://github.com/ring-clojure/ring) middleware that does routing based on
[swagger](http://swagger.io/) definitions.

## Usage

Add the swagger1st middleware into your ring handler chain and specify the schema location.

```clojure
(ns example
  (:require [ring.middleware.defaults :as ring]
            [io.sarnowski.swagger1st :as s1st]))

(def app
  (-> handler
    (ring/wrap-defaults ring/api-defaults)
    (s1st/swagger-routing ::s1st/json-file "example/swagger.json")))
```

The following swagger definition inputs are possible:

* **direct** - swagger definition already as clojure data structures
* **json** - a string containing a JSON formatted swagger definition
* **json-file** - a reference to a file, containing JSON
* **yaml** - a string containing a YAML formatted swagger definition
* **yaml-file** - a reference to a file, containing YAML

## License

Copyright (c) 2015, Tobias Sarnowski

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted,
provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
