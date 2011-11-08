(ns example.main
  (:require [framework.one :as fw1])
  (:use [ring.adapter.jetty])
  (:use [ring.middleware.reload]))

(defn -main[]
  (let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))] 
    (run-jetty
      (fw1/start :application-key "example"
                 :default-section "user")
      {:port port})))
