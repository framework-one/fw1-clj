(def project 'framework-one)
(def version "0.5.1-SNAPSHOT")

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
                            [selmer                "RELEASE"]])

(deftask build []
  (comp (pom) (jar) (install)))

(deftask deploy []
  (comp (pom) (jar) (push)))

(deftask run []
  (merge-env! :resource-paths #{"examples"})
  (require '[usermanager.main :as app])
  (apply (resolve 'app/-main) []))
