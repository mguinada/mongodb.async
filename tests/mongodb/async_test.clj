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

(defn insert-test-data!
  [db]
  (block!! (db/insert! db :test {:first-name "John" :last-name "Doe" :age 40}))
  (block!! (db/insert! db :test {:first-name "Jane" :last-name "Doe" :age 38}))
  (block!! (db/insert! db :test {:first-name "Johnny" :last-name "Doe" :age 6})))

(defn mongo-test-fixture
  [test]
  (log-level Level/SEVERE)
  (binding [*db* (db/connect :test-db {:host "127.0.0.1" :port 27017})]
    (try
      (insert-test-data! *db*)
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

(deftest fetch-all-test
  (testing "returns a collection of documents as a channel"
    (is (= [["John" "Doe"] ["Jane" "Doe"] ["Johnny" "Doe"]]
           (mapv names (<!! (db/fetch *db* :test)))))))

(deftest fetch-where-test
  (testing "Empty query map"
    (is (= [["John" "Doe"] ["Jane" "Doe"] ["Johnny" "Doe"]]
           (mapv names (<!! (db/fetch *db* :test :where {}))))))
  (testing "Simple equality"
    (is (= [["Jane" "Doe"]]
           (mapv names (<!! (db/fetch *db* :test :where {:first-name "Jane"}))))))
  (testing "Range of values and equality"
    (is (= [["John" "Doe"]]
           (mapv names (<!! (db/fetch *db* :test :where {:first-name "John"
                                                         :age {:$gte 38 :$lt 50}}))))))
  (testing "Logical OR"
    (is (= [["John" "Doe"] ["Johnny" "Doe"]]
           (mapv
            names
            (<!! (db/fetch *db* :test :where {:$or [{:first-name "John"} {:age 6}]}))))))
  (testing "Logical AND"
    (is (= [["Johnny" "Doe"]]
           (mapv
            names
            (<!! (db/fetch *db* :test :where {:$and [{:last-name "Doe"} {:age 6}]}))))))
  (testing "Logical NOT"
    (is (= [["John" "Doe"] ["Jane" "Doe"]]
           (mapv names (<!! (db/fetch *db* :test :where {:age {:$not {:$eq 6}}})))))))

(deftest fetch-one-test
  (testing "Fetch one"
    (is (= {:first-name "John" :last-name "Doe"}
           (select-keys
            (<!! (db/fetch *db* :test :where {:last-name "Doe"} :one? true))
            [:first-name :last-name]))))
  (testing "Short hand"
    (is (= (mapv names (<!! (db/fetch-one *db* :test :where {:last-name "Doe"})))
           (mapv names (<!! (db/fetch *db* :test :one? true :where {:last-name "Doe"}))))))
  (testing "Not found"
    (is (= :nil (<!! (db/fetch-one *db* :test :where {:last-name "nosuchname"}))))))

(deftest fetch-count-test
  (testing "Full count"
    (is (= 3 (<!! (db/fetch *db* :test :count? true)))))
  (testing "Filterd count"
    (is (= 2 (<!! (db/fetch *db* :test :where {:age {:$not {:$eq 6}}} :count? true)))))
  (testing "Short hand"
    (is (= 3 (<!! (db/fetch-count *db* :test))))
    (is (= (<!! (db/fetch-count *db* :test :where {:age {:$not {:$eq 6}}}))
           (<!! (db/fetch *db* :test :where {:age {:$not {:$eq 6}}} :count? true))))))

(deftest projection-test
  (is (= {:last-name "Doe" :age 38}
         (<!! (db/fetch-one *db* :test
                            :only [:last-name :age]
                            :where {:first-name "Jane"})))))

(deftest sorting-test
  (testing "Sort ascending"
    (is (= [["John" "Doe"] ["Jane" "Doe"] ["Johnny" "Doe"]]
           (mapv names (<!! (db/fetch *db* :test :sort {:age :desc}))))))
  (testing "Sort descending"
    (is (= [["Johnny" "Doe"] ["Jane" "Doe"] ["John" "Doe"]]
           (mapv names (<!! (db/fetch *db* :test :sort {:age :asc}))))))
  (testing "Sort ascending and descending"
    (is (= [["Jane" "Doe"] ["John" "Doe"] ["Johnny" "Doe"]]
           (mapv names (<!! (db/fetch *db* :test :sort {:first-name :asc :last-name :desc})))))))
