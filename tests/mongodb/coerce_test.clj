(ns mongodb.coerce-test
  (:require [clojure.test :refer :all]
            [mongodb.coerce :as c])
  (:import [org.bson Document]))

(deftest to-mongo-test
  (testing "maps"
    (let [m {:first-name "Joe" :age 18}]
      (is (= (Document. {"first-name" "Joe" "age" 18}) (c/to-mongo m)))))
  (testing "nested maps"
    (let [m {:name "Joe"
             :orders [{:number "321ZYX"} {:number "123XYZ"}]
             :address {:street "a street"
                       :city "a city"
                       :zip {:area "Z" :code "123"}}}]
      (is (= (Document. {"name" "Joe"
                         "orders"[(Document. {"number" "321ZYX"})
                                  (Document. {"number" "123XYZ"})]
                         "address" (Document. {"street" "a street"
                                               "city" "a city"
                                               "zip"
                                               (Document. {"area" "Z" "code" "123"})})})
             (c/to-mongo m))))))

(deftest to-clojure-test
  (testing "Documents"
    (let [doc (Document. {"first-name" "Joe" "age" 18})]
      (is (= {:first-name "Joe" :age 18} (c/to-clojure doc)))))
  (testing "Nested Documents"
    (let [doc (Document. {"name" "Joe"
                          "orders" (new java.util.ArrayList [(Document. {"number" "321ZYX"})
                                                             (Document. {"number" "123XYZ"})])
                          "address" (Document. {"street" "a street"
                                                "city" "a city"
                                                "zip"
                                                (Document. {"area" "Z" "code" "123"})})})]
      (is (= {:name "Joe"
              :orders [{:number "321ZYX"} {:number "123XYZ"}]
              :address {:street "a street"
                        :city "a city"
                        :zip {:area "Z" :code "123"}}}
             (c/to-clojure doc))))))
