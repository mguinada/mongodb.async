(set-env!
 :source-paths  #{"src" "tests"}
 :test-paths    #{"tests"}
 :dependencies '[[adzerk/boot-reload "0.4.12" :scope "test"]
                 [adzerk/boot-test "1.1.2" :scope "test"]
                 [metosin/boot-alt-test "0.2.1" :scope "test"]
                 [org.clojure/tools.namespace "0.2.11" :scope "test"]
                 [boot-codox "0.10.3" :scope "test"]
                 [org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
                 [org.clojure/tools.macro "0.1.2"]
                 [org.mongodb/mongodb-driver-async "3.4.2"]
                 [org.clojure/core.async "0.3.443"]])

(require
 '[boot.task.built-in    :refer [aot]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.boot-test      :refer [test]]
 '[metosin.boot-alt-test :refer [alt-test]]
 '[boot.git              :refer [last-commit]]
 '[codox.boot            :refer [codox]])

;;clojure namespace tools integration
(swap! boot.repl/*default-dependencies* conj
      '[org.clojure/tools.namespace "0.2.11"])

;; CIDER integration
(swap! boot.repl/*default-dependencies*
       concat '[[cider/cider-nrepl "0.14.0"]
                [refactor-nrepl "2.2.0"]])

(swap! boot.repl/*default-middleware*
       conj 'cider.nrepl/cider-middleware)

(set-env! :repositories [["clojars" {:url "https://clojars.org/repo/"
                                     :username (System/getenv "CLOJARS_USER")
                                     :password (System/getenv "CLOJARS_PASS")}]])

(def +version+ "0.0.1-SNAPSHOT")

(task-options!
  push {:repo           "clojars"
        :ensure-branch  "master"
        :ensure-clean   true
        :ensure-tag     (last-commit)
        :ensure-version +version+}
  pom {:project     'mguinada/mongodb.async
       :version     +version+
       :description "Asynchronous MongoDB client for Clojure"
       :url         "https://github.com/mguinada/mongodb.async"
       :scm         {:url "https://github.com/mguinada/mongodb.async"}
       :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})

;; Tasks
(deftask docs []
  (comp
   (codox
     :name "mongodb.async"
     :description "Asynchronous MongoDB client for Clojure"
     :source-uri "https://github.com/mguinada/mongodb.async"
     :source-paths #{"src"}
     :version +version+)
   (target)))

(deftask build []
  (comp ;;(docs) not working! troubleshoot.
        (pom)
        (jar)
        (install)))

(deftask run []
  (comp (watch)
        (build)
        (repl :server true)))

(deftask deploy []
  (comp (build)
        (push)))

(deftask run-tests
  [a autotest bool "If no exception should be thrown when tests fail"]
  (comp
   (alt-test :fail (not autotest))))

(deftask autotest []
  (comp
   (watch)
   (run-tests :autotest true)))

(deftask development []
  identity)

(deftask dev
  "Simple alias to run application in development mode"
  []
  (comp (development)
        (run)))
