(def project 'framework-one)
(def version "0.8.3")

(task-options!
 pom {:project     project
      :version     version
      :description "A lightweight, convention-based MVC web framework."
      :url         "https://github.com/framework-one/fw1-clj/"
      :scm         {:url "https://github.com/framework-one/fw1-clj/"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(set-env! :resource-paths #{"src"}
          :source-paths   #{"test"}
          :dependencies   '[[org.clojure/clojure "RELEASE" :scope "provided"]
                                        ; render as xml
                            [org.clojure/data.xml "0.1.0-beta2"]
                                        ; render as JSON
                            [cheshire            "5.6.3"]
                                        ; core web request handling
                            [ring                "1.6.0-beta6"]
                            [ring/ring-defaults  "0.2.1"]
                                        ; view/layout templates
                            [selmer              "1.0.9"]
                                        ; standardized application start/stop
                            [com.stuartsierra/component "0.3.1"]
                                        ; standardized routing
                            [compojure           "1.6.0-beta1"]
                            [http-kit            "2.2.0" :scope "test"]
                            [seancorfield/boot-expectations "RELEASE" :scope "test"]
                            [org.clojure/test.check "RELEASE" :scope "test"]])

(require '[seancorfield.boot-expectations :refer [expectations expecting]])

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
  [p port   PORT   int "The port on which to run the server."
   s server SERVER str "The server type to use."]
  (comp (examples)
        (let [port (or port 8080)]
          (require '[usermanager.main :as app])
          ((resolve 'app/-main) port server)
          identity)))
