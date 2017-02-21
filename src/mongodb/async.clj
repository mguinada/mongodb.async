(ns mongodb.async
  (:require [mongodb.coerce :as c]
            [clojure.core.async :as async]
            [clojure.walk :as w])
  (:import [org.bson Document]
           [com.mongodb Block]
           [com.mongodb.async SingleResultCallback]
           [com.mongodb.async.client MongoClients]))

(deftype Connection [client db])

(defn connect
  "Connects to a database.

   This function receives the database name to connect to and optionaly a map
   with connection properties.

   Examples:

   (connect :local-database)
   (connect :some-database {:host '192.168.10.10' :port 27017})
  "
  ([database] (connect database {}))
  ([database {:keys [host port] :or {host "127.0.0.1" port 27017}}]
   (let [client (MongoClients/create (str "mongodb://" (name host) ":" port))]
     (Connection. client (.getDatabase client (name database))))))

(defn close!
  "Closes the connection"
  [^Connection conn]
  (-> conn .client .close))

(defn- collection
  [^Connection conn dbcol]
  (.getCollection (.db conn) (name dbcol)))

(defn- fetch-iterable
  [^Connection conn dbcol cb]
  (.find (collection conn dbcol)))

(defmacro ^:private resultfn
  [[result exception] & body]
  `(reify SingleResultCallback
     (onResult [_ ~result ~exception]
       (~@body))))

(defn- resultch
  [f & args]
  (let [ch (async/chan 1)
        cb (fn [rs ex]
             (-> ch
              (async/put! (or ex rs))
              (async/close!)))]
    (apply f (concat args [cb]))
    ch))

(defn insert!
  "Inserts `data` into collection `coll`."
  ([^Connection conn coll data]
   (resultch insert! conn coll (c/to-mongo data)))
  ([^Connection conn coll data cb]
   (let [doc (c/to-mongo data)]
     (-> (collection conn coll)
         (.insertOne
          doc
          (resultfn [_ ex]
                    (cb (c/to-clojure doc) ex)))))))

(defn drop-collection!
  "Drops a collection"
  ([^Connection conn coll]
   (let [ch (async/chan 1)]
     (drop-collection!
      conn
      coll
      (fn [_ ex]
        (-> ch
            (async/put! (if (nil? ex) coll ex))
            (async/close!))))
     ch))
  ([^Connection conn coll cb]
   (-> (collection conn coll)
       (.drop (resultfn [rs ex] (cb rs ex))))))

(defn fetch
  "Fetches data from collection `coll`"
  ([^Connection conn coll]
   (resultch fetch conn coll))
  ([conn coll cb]
   (-> (fetch-iterable conn coll cb)
       (.into
        (new java.util.ArrayList)
        (resultfn [result ex]
                  (cb (c/to-clojure result) ex))))))
