(ns fw1-test.core
  (:require [fw1-clj.core :as fw1])
  (:use [ring.adapter.jetty])
  (:use [ring.middleware.reload]))

(defn -main[]
  (let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))] 
    (run-jetty (wrap-reload (var fw1/app) '(fw1-clj.core)) {:port port})))
