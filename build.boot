(set-env!
 :source-paths  #{"src" "tests"}
 :test-paths    #{"tests"}
 :dependencies '[[metosin/boot-alt-test "0.3.2" :scope "test"]
                 [org.clojure/tools.namespace "0.3.0-alpha3" :scope "test"]
                 [boot-codox "0.10.3" :scope "test"]
                 [org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
                 [org.mongodb/mongodb-driver-async "3.4.2"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.macro "0.1.2"]])

(require
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

(def +version+ "0.0.1-SNAPSHOT")
(def +description+ "Asynchronous MongoDB client for Clojure")
(def +url+ "https://github.com/mguinada/mongodb.async")

(task-options!
  push {:repo           "clojars"
        :ensure-branch  "master"
        :ensure-clean   true
        :ensure-tag     (last-commit)
        :ensure-version +version+
        :repo-map {:url "https://clojars.org/repo/"
                   :username (System/getenv "CLOJARS_USER")
                   :password (System/getenv "CLOJARS_PASS")}}
  pom {:project     'mguinada/mongodb.async
       :version     +version+
       :description +description+
       :url         +url+
       :scm         {:url +url+}
       :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})

;; Tasks
(deftask docs []
  (comp
   (codox
     :name "mongodb.async"
     :description +description+
     :source-uri +url+
     :source-paths #{"src"}
     :version +version+)
   (target)))

(deftask build []
  (comp (docs)
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
   (alt-test)))

(deftask autotest []
  (comp
   (watch)
   (run-tests :autotest true)))
