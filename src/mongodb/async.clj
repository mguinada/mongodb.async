(ns ^{:author "Miguel Guinada <mguinada@gmail.com>"} mongodb.async
  "Thin wrapper for MongoDB's java async driver that enables idiomatic
  asynchronous operation over MongoDB via callbacks or `core.async channels`"
  (:require [mongodb.async.coerce :as c]
            [clojure.core.async :as async])
  (:import [com.mongodb.async SingleResultCallback]
           [com.mongodb.async.client MongoClients]
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

(defn- result-chan
  [f & args]
  (let [ch (async/chan 1)
        cb (fn [rs ex]
             (-> ch
                 (async/put! (or ex rs :nil))
                 (async/close!)))]
    (apply f (concat args [cb]))
    ch))

(defn- fetch-iterable
  [^Connection conn coll ^Document query]
  {:pre [(doc? query)]}
  (.find (collection conn coll) query))

(defn- count*
  [^Connection conn coll ^Document query cb]
  {:pre [(doc? query)]}
  (.count
   (collection conn coll)
   query
   (result-fn
    [rs ex]
    (cb (c/to-clojure rs) ex))))

(defn insert!
  "Inserts `data` into collection `coll`"
  ([^Connection conn coll data]
   (result-chan insert! conn coll (c/to-mongo data)))
  ([^Connection conn coll data cb]
   (let [doc (c/to-mongo data)]
     (-> (collection conn coll)
         (.insertOne
          doc
          (result-fn
           [_ ex]
           (cb (c/to-clojure doc) ex)))))))

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

(defn fetch
  "Fetches data from collection `coll`."
  [^Connection conn coll & opts]
  (let [{:keys [where count? one?]
         :or {where {} count? false one? false}} (remove fn? opts)
        cb (first (filter fn? opts))
        query (c/to-mongo where)]
    (cond
      count?
      (if (nil? cb)
        (result-chan count* conn coll query)
        (count* conn coll query cb))
      :else
      (if (nil? cb)
        (result-chan fetch conn coll :where query :one? one?)
        (let [it (fetch-iterable conn coll query)
              rsfn (result-fn [rs ex] (cb (c/to-clojure rs) ex))]
          (if one?
            (.first it rsfn)
            (.into it (java.util.ArrayList.) rsfn)))))))

(defn- args-concat
  [^Connection conn coll opts & extra]
  (concat [conn coll] (concat opts extra)))

(defn fetch-count
  "Counts the number of documents in the collection.
   Optionaly a query map can be provided."
  [^Connection conn coll & opts]
  (apply fetch (args-concat conn coll opts :count? true)))

(defn fetch-one
  "Fetches the first document that complies to the query criteria
   or `nil` of no document is found if using a callback or `:nil`
   will be placed in the channel when using this particular interface."
  [^Connection conn coll & opts]
  (apply fetch (args-concat conn coll opts :one? true)))
