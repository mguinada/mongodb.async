(ns mongodb.async-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async :refer [<!!]]
            [mongodb.async :as db])
  (:import [org.bson Document]
           [java.util.logging Logger Level]))

(defn log-level
  ([]
   (log-level Level/INFO))
  ([level]
   (-> (Logger/getLogger "org.mongodb.driver")
       (.setLevel level))))

(defn block!!
  [ch]
  (let [r (<!! ch)]
    (when (instance? Throwable r) (throw r))
    r))

(def ^:dynamic *db*)

(defn mongo-test-fixture
  [test]
  (log-level Level/SEVERE)
  (binding [*db* (db/connect :test-db {:host "127.0.0.1" :port 27017})]
    (try
      (test)
      (finally
        (do
          (block!! (db/drop-collection! *db* :users))
          (block!! (db/drop-collection! *db* :test))
          (db/close! *db*)
          (log-level Level/INFO))))))

(use-fixtures :each mongo-test-fixture)

(def names (juxt :first-name :last-name))

(deftest insert!-test
  (testing "inserts a document into a collection using a callback"
    (db/insert!
     *db*
     :users
     {:first-name "John" :last-name "Doe"}
     (fn [r ex]
       (is (= ["John" "Doe"]
              (names r))))))
  (testing "inserts a document into a collection using a channel"
    (is (= ["John" "Doe"]
           (names (<!! (db/insert!
                        *db*
                        :users
                        {:first-name "John" :last-name "Doe"})))))))

(deftest fetch-test
  (do
    (block!! (db/insert! *db* :test {:first-name "John" :last-name "Doe"}))
    (block!! (db/insert! *db* :test {:first-name "Jane" :last-name "Doe"}))
    (testing "returns a collection of documents as a channel"
      (is (= [["John" "Doe"] ["Jane" "Doe"]] (mapv names (<!! (db/fetch *db* :test))))))))
