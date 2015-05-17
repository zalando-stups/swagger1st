# swagger1st component example

This example implements a TODO list, integrating the
[component lifecycle framework](https://github.com/stuartsierra/component).

# Running the example

First, you have to build an uberjar, that embeds an HTTP server:

    $ lein run -m todo.core

Navigate to [the UI](http://localhost:8080/ui/) to get an overview via the Swagger UI.

## Things to look at

The following files are of special interest:

* [/src/todo/api.clj](src/todo/api.clj)
    * All functions take not only a `request` parameter but also a `db` parameter which is the injected database.
* [/src/todo/core.clj](src/todo/core.clj)
    * The API component manages the embedded HTTP server, also configuring the swagger1st middlewares.
    * The special configuration to focus on is the `s1st/executor` `:resolver`.
        * The configured function is defined above, wrapping the default function resolution in a new function, adding
          the DB dependency as a second parameter.

