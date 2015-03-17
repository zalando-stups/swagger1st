# swagger1st component example

This example implements a TODO list, integrating the
[component lifecycle framework](https://github.com/stuartsierra/component).

# Running the example

First, you have to build an uberjar, that embeds an HTTP server:

    $ lein uberjar
    $ java -jar target/todo.jar

Navigate to [the UI](http://localhost:3000/ui/) to get an overview via the Swagger UI.

## Things to look at

The following files are of special interest:

* [/src/todo/api.clj](src/todo/api.clj)
    * The API component manages the embedded HTTP server, also configuring the swagger1st middlewares.
    * The special configuration to focus on is the `swagger-executor` `:mappers`.
        * The configured function is defined above, wrapping the default function resolution in a new function, adding
          the DB dependency as a second parameter.
    * Below the component definition is the actual business logic. Note, that the function take 2 arguments instead of
      only the request as defined above. The functions will always take the database as a second argument.
