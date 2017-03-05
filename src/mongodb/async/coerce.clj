(ns mongodb.async.coerce
  "Clojure/MongoDB - MongoDB/Clojure data coercion"
  (:import [org.bson Document]))

(defn- coerce-map
  "Takes a map and returns another one to which
  `kfn` function was applied to `m` keys and `vfn` was applied to `m` values"
  [m kfn vfn]
  (reduce (fn [m* [k v]] (assoc m* (kfn k) (vfn v))) {} m))

(defprotocol ToMongo
  (to-mongo [this] "Coerces clojure data structures to it's mongodb representation"))

(extend-protocol ToMongo
  nil
  (to-mongo [_]
    nil)
  clojure.lang.IPersistentMap
  (to-mongo [m]
    (Document. (coerce-map m name to-mongo)))
  clojure.lang.IPersistentVector
  (to-mongo [v]
    (mapv (fn [e] (to-mongo e)) v))
  java.lang.Object
  (to-mongo [obj]
    obj))

(defprotocol ToClojure
  (to-clojure [this] "Coerces mongodb data structures to it's clojure representation"))

(extend-protocol ToClojure
  nil
  (to-clojure [_]
    nil)
  Document
  (to-clojure [doc]
    (coerce-map (into {} doc) keyword to-clojure))
  java.util.ArrayList
  (to-clojure [array]
    (mapv to-clojure array))
  java.lang.Object
  (to-clojure [obj]
    obj))

(defn projection
  "Coerces to a projection specification document"
  [v]
  (if (empty? v)
    (to-mongo {})
    (let [m (zipmap v (repeat 1))]
      (to-mongo (if-not (contains? (set (keys m)) :_id) (assoc m :_id 0) m)))))

(defn sorting
  "Coerces to a sorting specifiction document"
  [sort]
  (letfn [(coerce [m [k v]]
            (let [dir (case v
                        :asc 1
                        :desc -1
                        1 1
                        -1 -1
                        (throw (ex-info (str "Can't sort " v)
                                        {:type :sorting
                                         :cause sort})))]
              (assoc m k dir)))]
    (to-mongo (reduce coerce {} sort))))
