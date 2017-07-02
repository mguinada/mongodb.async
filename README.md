# mongodb.async 
<br/>[![Clojars Project](https://img.shields.io/clojars/v/mguinada/mongodb.async.svg)](https://clojars.org/mguinada/mongodb.async) [![Build Status](https://travis-ci.org/mguinada/mongodb.async.svg?branch=master)](https://travis-ci.org/mguinada/mongodb.async)

Asynchronous MongoDB client for Clojure

### Disclaimer

Not production ready. API can be the subject of changes. Use at your own risk.

### Usage

All functions that map to MongoDB operations dispatch on double arity.

They can either be called with a callback `(fn [result exception] ...)` as the last argument
or the call can omit the callback and thus return a [core.async](https://github.com/clojure/core.async) channel.

Here are some usage samples over the MongoDB [primer dataset](https://docs.mongodb.com/getting-started/shell/import-data/)

##### connect

```clojure
(require '[mongodb.async :as m])
(require '[clojure.core.async :as async :refer [<!!]])

(def db (m/connect :test))
```

##### fetch one (with callback)

```clojure
(m/fetch-one db :restaurants :where {:cuisine "Continental"} (fn [rs ex]
                                                              (if-not ex
                                                                (println "result:" rs)
                                                                (println "error:" ex))))
```

##### fetch many

```clojure
(<!! (m/fetch db :restaurants :where {:$or [{:cuisine "Continental"} {:cuisine "Mediterranean"}]} :limit 3))
```

##### count

```clojure
(<!! (m/fetch-count db :restaurants :where {:cuisine "Continental"}))
```

##### insert

```clojure
(<!! (m/insert! db :restaurants {:name "Catering Inc." :cuisine "American"}))
```

##### insert many

```clojure
(<!! (m/insert-many! db :restaurants [{:name "Domino's Pizza" :cuisine "Pizza"} {:name "Pizza Hut" :cuisine "Pizza"}]))
```

##### replace

```clojure
(<!! (m/replace-one! db :restaurants {:name "Catering"} :where {:name "Catering Inc."}))
```

##### remove

```clojure
(<!! (m/remove! db :restaurants :where {:name "Catering Inc."}))
```

##### remove one

```clojure
(<!! (m/remove-one! db :restaurants :where {:name "Domino's Pizza"}))
```

### What's missing

* Database authentication
* Aggregation primitives
* Map/Reduce primitives
* Support for DbRefs
* Write concerns
* Read preferences
* Add/Remove index

### Influences

* [congomongo](https://github.com/aboekhoff/congomongo)
* [postgres.async](https://github.com/alaisi/postgres.async)

### Resources

* [http://www.allanbank.com/mongodb-async-driver/usage.html]
* [https://github.com/allanbank/mongodb-async-examples]
* [http://stackoverflow.com/questions/33257459/mongodb-async-java-driver-find]

### License

Copyright © 2017 Miguel Guinada<br/>
Distributed under the [Eclipse Public License][]

[Eclipse Public License]: https://github.com/mguinada/mongodb.async/blob/master/LICENSE
