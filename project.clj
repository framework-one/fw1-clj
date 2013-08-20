(defproject framework-one "0.2.2"
  :description "A lightweight, convention-based MVC web framework."
  :url "https://github.com/framework-one/fw1-clj/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dev-resources-path "examples"
  :profiles {:dev {:resource-paths ["examples"]
                   :dependencies []}}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.2"]
                 [org.clojure/data.xml "0.0.7"]
                 [ring "1.0.2"]
                 [enlive "1.0.0"]
                 [selmer "0.4.0"]])
