(ns ^{:author "Miguel Guinada <mguinada@gmail.com>"} mongodb.async
  "Thin wrapper for MongoDB's java async driver that enables idiomatic
  asynchronous operation over MongoDB via callbacks or `core.async channels`"
  (:require [mongodb.async.coerce :as c]
            [clojure.core.async :as async]
            [clojure.tools.macro :as m])
  (:import [com.mongodb.async SingleResultCallback]
           [com.mongodb.async.client MongoClients]
           [com.mongodb.client.model UpdateOptions]
           [org.bson Document]))

(deftype Connection [client db])

(defn connect
  "Connects to a database.

   This function receives the database name to connect to and optionaly a map
   with connection properties. A connection to the database will be returned.

   Examples:

   (connect :local-database)
   (connect \"some-database\" {:host '192.168.10.10' :port 27017})"
  ([database]
   (connect database {}))
  ([database {:keys [host port] :or {host "127.0.0.1" port 27017}}]
   (let [client (MongoClients/create (str "mongodb://" (name host) ":" port))]
     (Connection. client (.getDatabase client (name database))))))

(defn close!
  "Closes the connection"
  [^Connection conn]
  (-> conn .client .close))

(defn collection
  "Gets a collection"
  [^Connection conn dbcol]
  (.getCollection (.db conn) (name dbcol)))

(defn- doc?
  "Returns true is `x` is an instance of `org.bson.Document`"
  [x]
  (instance? Document x))

(defmacro ^:private result-fn
  [[result exception] & body]
  `(reify SingleResultCallback
     (onResult [_ ~result ~exception]
       (~@body))))

(defmacro ^:no-doc defop
  "`defop` defines `mongo.async` functions that consist of mongo operations
  (e.g. `fetch`, `count`, etc). These function signatures are somehow atypical.
  They may take positional arguments, keyword arguments and another final positional
  argument that takes a callback function. This is not supported by clojure's map
  destructuring construct, so `defop` provides the syntatic sugar to do this.

  example:

  (defop f
    \"Two positinal arguments, two optionals interleaved with default values
     and a final positional argument\"
    [pos1 pos2 :opt1 {} :opt2 :opt2-default f]
    {:pos1 pos1 :pos2 pos2 :opt1 opt1 :opt2 opt2 :f f})

  (f :a :b :opts1 :x :tail)
  ;; => {:pos1 :a, :pos2 :b, :opt1 {}, :opt2 :opt2-default, :f :tail}
  "
  [fn-name & fn-tail]
  (let [[fn-name [args & body]] (m/name-with-attributes fn-name fn-tail)
        [positionals tail] (split-with symbol? args)
        [options [tail-pos]] (split-with (complement symbol?) tail)
        tail-pos (if (nil? tail-pos) (gensym "tail-pos") tail-pos)
        syms (map #(-> % name symbol) (take-nth 2 options))
        vals (take-nth 2 (rest options))
        destruct-map {:keys (vec syms) :or (apply hash-map (interleave syms vals))}]
    `(defn ~fn-name
       {:arglists '([~@positionals ~destruct-map] [~@positionals ~destruct-map ~tail-pos])}
       [~@positionals & options#]
       (let [~destruct-map (apply hash-map (remove fn? options#))
             [~tail-pos] (filter fn? options#)]
         ~@body))))

(defn- apply-op
  [f args]
  (apply f (remove nil? args)))

(defn- result-chan
  [f & args]
  (let [ch (async/chan 1)
        cb (fn [rs ex]
             (async/put! ch (or ex rs :nil))
             (async/close! ch))]
    (apply f (concat args [cb]))
    ch))

(defn- fetch-iterable
  ([^Connection conn coll ^Document query ^Document projection ^Document sorting]
   (fetch-iterable conn coll query projection sorting 0 0))
  ([^Connection conn coll ^Document query ^Document projection ^Document sorting skip limit]
   {:pre [(doc? query) (doc? projection) (doc? sorting)]}
   (let [it (collection conn coll)
         it (if-not (empty? query) (.find it query) (.find it))
         it (if-not (empty? projection) (.projection it projection) it)
         it (if-not (empty? sorting) (.sort it sorting) it)]
     (-> it (.skip skip) (.limit limit)))))

(defn- count*
  [^Connection conn coll ^Document query cb]
  {:pre [(doc? query)]}
  (.count
   (collection conn coll)
   query
   (result-fn
    [rs ex]
    (cb (c/to-clojure rs) ex))))

(defop insert!
  "Inserts `data` into collection `coll`.
   If the options :many is true, and a vector is provided as data,
   a mass insert will be performed"
  [^Connection conn coll data :many false cb]
  (if (nil? cb)
    (result-chan insert! conn coll data :many many)
    (let [db-coll (collection conn coll)
          doc (c/to-mongo data)
          rfn (result-fn [_ ex] (cb (c/to-clojure doc) ex))]
      (if many
        (.insertMany db-coll doc rfn)
        (.insertOne db-coll doc rfn)))))

(defop insert-many!
  "Mass insert"
  [^Connection conn coll data]
  (apply-op insert! [conn coll data :many true]))

(defn drop-collection!
  "Drops a collection"
  ([^Connection conn coll]
   (let [ch (async/chan 1)
         cb (fn [_ ex]
              (-> ch
                  (async/put! (if (nil? ex) coll ex))
                  (async/close!)))]
     (drop-collection! conn coll cb)
     ch))
  ([^Connection conn coll cb]
   (-> (collection conn coll)
       (.drop (result-fn [rs ex] (cb rs ex))))))

(defop replace-one!
  "Replaces a single document within the collection based on the filter.
   Will perform an upsert if `:upsert?` equals true. Defaults to false.

   Returns a map with the following data:

   :acknowledged - true if the update was acknowledged by the server
   :matched-count - number of documents that matched the critera
   :upserted-id - the id of the upserted document if an upsert was performed"
  [^Connection conn coll replacement :where {} :upsert? false cb]
  (if (nil? cb)
    (result-chan replace-one! conn coll replacement :where where :upsert? upsert?)
    (let [query (c/to-mongo where) data (c/to-mongo replacement)
          replace-opts (.upsert (UpdateOptions.) upsert?)]
      (-> (collection conn coll)
          (.replaceOne
           query
           data
           replace-opts
           (result-fn
            [rs ex]
            (cb (c/to-clojure rs) ex)))))))

(defop remove!
  "Removes documents from a collection
   Optional arguments:

   :where - a query map
   :one? - delete only the first document the complies to the query, defaults to `false`"
  [^Connection conn coll :where {} :one? false cb]
  (if (nil? cb)
    (result-chan remove! conn coll :where where :one? one?)
    (let [query (c/to-mongo where)
          rsfn (result-fn [rs ex] (cb (.getDeletedCount rs) ex))
          it (collection conn coll)]
      (if one?
        (.deleteOne it query rsfn)
        (.deleteMany it query rsfn)))))

(defop fetch
  "Fetches data from collection `coll`
   Optional arguments:

   :where - a query map
   :only - a vector of keys to project
   :sort - a map with sorting specs
   :skip - number of records to skip
   :limit - number of records to return
   :count? - performs a document count, defaults to `false`
   :one? - retreive only the first document, defaults to `false`
   :explain? - returns the query explain data, defaults to `false`"
  [^Connection conn coll
   :where {} :only [] :sort {} :skip 0 :limit 0 :count? false :one? false :explain? false cb]
  (cond
    count?
    (let [query (c/to-mongo where)]
      (if (nil? cb)
        (result-chan count* conn coll query)
        (count* conn coll query cb)))
    :else
    (if (nil? cb)
      (result-chan fetch conn coll
                   :where where
                   :one? one?
                   :only only
                   :sort sort
                   :skip skip
                   :limit limit
                   :explain? explain?)
      (let [query (c/to-mongo where)
            proj (c/projection only)
            sorting (c/sorting sort)
            rsfn (result-fn [rs ex] (cb (c/to-clojure rs) ex))
            it (fetch-iterable conn coll query proj sorting skip limit)]
        (if explain?
          (.first (.modifiers it (c/to-mongo {:$explain true})) rsfn)
          (if one?
            (.first it rsfn)
            (.into it (java.util.ArrayList.) rsfn)))))))

(defn- apply-op
  [f args]
  (apply f (remove nil? args)))

(defop fetch-count
  "Counts the number of documents in the collection.
   Optionaly a query map can be provided."
  [^Connection conn coll :where {} cb]
  (apply-op fetch [conn coll :where where :count? true cb]))

(defop fetch-one
  "Fetches the first document that complies to the query criteria
   or `nil` of no document is found if using a callback or `:nil`
   will be placed in the channel when using this particular interface."
  [^Connection conn coll :where {} :only [] :explain? false cb]
  (apply-op fetch [conn coll :where where :only only :one? true :explain? explain? cb]))

(defop remove-one!
  "Remove the first document that complies to the query"
  [^Connection conn coll :where {} :explain? false cb]
  (apply-op remove! [conn coll :where where :one? true :explain? explain? cb]))
