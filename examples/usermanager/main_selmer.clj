(ns usermanager.main-selmer
  (:require [framework.one :as fw1]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn -main[]
  (let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))] 
    (run-jetty
      (fw1/start :application-key "usermanager"
                 :home "user.default"
                 :suffix "tpl"
                 :before (fn [rc]
                           (when (fw1/reload? rc)
                             (require 'usermanager.model.user-manager :reload))
                           rc))
      {:port port})))
