(ns fw1-test.core
  (:require [framework.one :as fw1])
  (:use [ring.adapter.jetty])
  (:use [ring.middleware.reload]))

(defn -main[]
  (let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))] 
    (run-jetty
     (wrap-reload
      (fw1/start :reload-application-on-every-request true)
      '(framework.one))
     {:port port})))
