(defproject framework-one "0.1.0"
  :description "A lightweight, convention-based MVC web framework."
  :dev-resources-path "examples"
  :profiles {:dev {:resource-paths ["examples"]
                   :dependencies []}}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [ring "1.0.2"]
                 [enlive "1.0.0"]])
