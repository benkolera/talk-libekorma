# Libekorma

This is a sample project whose only purpose is to show how one combines Liberator and Korma projects to build a RESTful webservice in Clojure around a relational database.

The code has been adapted from https://github.com/aturok/libekorma

## Getting started

1. [Install Leinigan](http://leiningen.org/#install)
1. Set up the DB with: `lein migrate`
1. Start the server with: `lein ring server`
1. Follow along to [app.clj](/src/libekorma/app.clj).
1. To test the server, hit [](http://localhost:3000/tasks).
