(set-env!
 :project 'irresponsible/thyroid
 :version "0.1.0"
 :resource-paths #{"src" "resources"}
 :source-paths #{"src"}
 :dependencies '[[org.clojure/clojure     "1.9.0-alpha17"]
                 [org.thymeleaf/thymeleaf "3.0.6.RELEASE"]
                 ;; Development dependencies
                 [adzerk/boot-test         "1.2.0" :scope "test"]
                 [irresponsible/domiscuity "0.2.0" :scope "test"]])

(require '[adzerk.boot-test :as t])

(task-options!
  pom  {:project (get-env :project)
        :version (get-env :version)
        :description "HTML5 templating made differently insane"
        :url "https://github.com/irresponsible/thyroid"
        :scm {:url "https://github.com/irresponsible/thyroid"}
        :license {"MIT" "https://en.wikipedia.org/MIT_License"}}
  push {:tag true
        :ensure-branch "master"
        :ensure-release true
        :ensure-clean true
        :gpg-sign true
        :repo "clojars"}
  target {:dir #{"target"}})

(deftask testing []
  (set-env! :source-paths  #(conj % "test")
            :resource-paths #(conj % "test"))
  identity)

(deftask test []
  (comp
   (testing)
   (t/test)))
