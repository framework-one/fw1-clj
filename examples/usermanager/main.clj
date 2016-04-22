(ns usermanager.main
  (:require [framework.one :as fw1]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty :refer [run-jetty]]))

;; implement your application's lifecycle here
(defrecord Application [state config information]
  component/Lifecycle
  (start [this]
    (assoc this :state "Running"))
  (stop  [this]
    (assoc this :state "Stopped")))

(defn my-application
  "Return your application component, fully configured."
  [config]
  (map->Application {:information "Some Data"
                     :config      config}))

(defn my-handler
  "Build the FW/1 handler from an application (component).
  Provide your application's configuration here."
  [application]
  (fw1/start {:application-key "usermanager"
              :application application
              :home "user.default"
              :before (fn [rc]
                        (when (fw1/reload? rc)
                          (require 'usermanager.model.user-manager :reload))
                        rc)}))

;; lifecycle for the Jetty server in which we run
(defrecord WebServer [port join? http-server application]
  component/Lifecycle
  (start [this]
    (if (:http-server this)
      this
      (assoc this :http-server (run-jetty (my-handler application)
                                          {:port port :join? join?}))))
  (stop  [this]
    (if (:http-server this)
      (do
        (.stop (:http-server this))
        (assoc this :http-server nil))
      this)))

(defn web-server
  "Return a WebServer component that depends on the application."
  [port join?]
  (component/using (map->WebServer {:port port :join? join?})
                   [:application]))

;; when working in the REPL you can create a system on any port
;; and then start and stop it as necessary
(defn new-system
  "Build a default system to run."
  ([port] (new-system port false))
  ([port join?]
   (component/system-map :application (my-application {:port port :repl? (not join?)})
                         :web-server  (web-server port join?))))

(defn -main
  [& [port]]
  (let [port (or port (get (System/getenv) "PORT" 8080))
        port (cond-> port (string? port) Integer/parseInt)]
    (component/start (new-system port true))))
