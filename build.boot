(def project 'framework-one)
(def version "0.5.3-SNAPSHOT")

(task-options!
 pom {:project     project
      :version     version
      :description "A lightweight, convention-based MVC web framework."
      :url         "https://github.com/framework-one/fw1-clj/"
      :scm         {:url "https://github.com/framework-one/fw1-clj/"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(set-env! :resource-paths #{"src"}
          :dependencies   '[[org.clojure/clojure   "RELEASE"]
                            [org.clojure/data.json "RELEASE"]
                            [org.clojure/data.xml  "RELEASE"]
                            [ring                  "RELEASE"]
                            [selmer                "RELEASE"]
                            [seancorfield/boot-expectations "RELEASE" :scope "test"]])

(require '[seancorfield.boot-expectations :refer [expectations]])

(deftask build []
  (comp (pom) (jar) (install)))

(deftask deploy []
  (comp (pom) (jar) (push)))

(deftask run []
  (merge-env! :resource-paths #{"examples"})
  (require '[usermanager.main :as app])
  (apply (resolve 'app/-main) []))

(deftask with-test []
  (merge-env! :source-paths #{"test"}
              :dependencies '[[expectations "RELEASE"]])
  identity)

(ns-unmap *ns* 'test)

(deftask test []
  (comp (with-test)
        (expectations :verbose true)))
