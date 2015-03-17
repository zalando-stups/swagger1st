# swagger1st helloworld example

This examples shows the most basic usage of swagger1st.

## Running the example

Using the [lein-ring](https://github.com/weavejester/lein-ring) plugin, you can easily start the application:

    $ lein ring server

Navigate to [the UI](http://localhost:3000/ui/) to get the Swagger UI for discovery.

## Things to look at

The following files are of special interest:

* [/project.clj](project.clj)
    * Contains the swagger1st dependency and the [lein-ring](https://github.com/weavejester/lein-ring) configuration for
      easy starting.
* [/resources/helloworld-api.yaml](resources/helloworld-api.yaml)
    * The API swagger definition that we want to implement.
    * Note the `operationId` attributes on operations. Those are function names that can directly mapped to your clojure
      code.
* [/src/helloworld.clj](src/helloworld.clj)
    * The `app` definition is the basic ring handler setup, pointing to the API definition.
    * Above the `app` definition, you find the actual business logic that is connected via the `operationId` attribute
      in the API definition.
