(ns usermanager.main
  (:require [framework.one :as fw1]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn -main
  [& [port]]
  (let [port (or port (get (System/getenv) "PORT" 8080))
        port (cond-> port (string? port) Integer/parseInt)]
    (run-jetty
     (fw1/start {:application-key "usermanager"
                 :home "user.default"
                 :before (fn [rc]
                           (when (fw1/reload? rc)
                             (require 'usermanager.model.user-manager :reload))
                           rc)})
     {:port port})))
