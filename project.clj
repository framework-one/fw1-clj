(defproject framework-one "0.4.0"
  :description "A lightweight, convention-based MVC web framework."
  :url "https://github.com/framework-one/fw1-clj/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dev-resources-path "examples"
  :profiles {:dev {:resource-paths ["examples"]
                   :dependencies []}}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.json "0.2.4"]
                 [org.clojure/data.xml "0.0.7"]
                 [ring "1.2.1"]
                 [selmer "0.5.9"]])
