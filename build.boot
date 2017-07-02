(set-env!
 :source-paths  #{"src" "tests"}
 :test-paths    #{"tests"}
 :dependencies '[[adzerk/boot-reload "0.4.12" :scope "test"]
                 [adzerk/boot-test "1.1.2" :scope "test"]
                 [metosin/boot-alt-test "0.2.1" :scope "test"]
                 [org.clojure/tools.namespace "0.2.11" :scope "test"]
                 [boot-codox "0.10.3" :scope "test"]
                 [org.clojure/tools.macro "0.1.2"]
                 [org.mongodb/mongodb-driver-async "3.4.2"]
                 [org.clojure/core.async "0.3.443"]])

(require
 '[boot.task.built-in    :refer [aot]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.boot-test      :refer [test]]
 '[metosin.boot-alt-test :refer [alt-test]]
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

;; Tasks
(deftask docs []
  (comp
   (codox :name "mongodb.async" :source-paths #{"src"})
   (target)))

(deftask build []
  (comp (speak)
        (docs)
        (aot)))

(deftask run []
  (comp (watch)
        (repl)
        (reload)
        (build)))

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
