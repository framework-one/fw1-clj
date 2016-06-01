(def project 'framework-one)
(def version "0.6.0")

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
                            [org.clojure/data.xml  "RELEASE"]
                            [cheshire              "RELEASE"]
                            [ring                  "RELEASE"]
                            [selmer                "RELEASE"]
                            [seancorfield/boot-expectations "RELEASE" :scope "test"]])

(require '[seancorfield.boot-expectations :refer [expectations]])

(deftask build []
  (comp (pom) (jar) (install)))

(deftask deploy []
  (comp (pom) (jar) (push)))

(deftask examples []
  (merge-env! :resource-paths #{"examples"}
              :dependencies   '[[org.clojure/java.jdbc  "RELEASE"]
                                [org.apache.derby/derby "RELEASE"]])
  identity)

(deftask run
  [p port PORT int "The port on which to run the server."]
  (comp (examples)
        (let [port (or port 8080)]
          (require '[usermanager.main :as app])
          (apply (resolve 'app/-main) [port])
          identity)))

(deftask with-test []
  (merge-env! :source-paths #{"test"}
              :dependencies '[[expectations "RELEASE"]])
  identity)

(ns-unmap *ns* 'test)

(deftask test []
  (comp (with-test)
        (expectations :verbose true)))
