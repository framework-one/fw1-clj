(ns usermanager.main
  (:require [framework.one :as fw1]
            [usermanager.model.user-manager :as model]
            [com.stuartsierra.component :as component]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]))

;; implement your application's lifecycle here
(defrecord Application [state config application-name]
  component/Lifecycle
  (start [this]
    ;; set up database if necessary
    (model/setup-database)
    (assoc this :state "Running"))
  (stop  [this]
    (assoc this :state "Stopped")))

(defn my-application
  "Return your application component, fully configured."
  [config]
  (map->Application {:application-name "usermanager"
                     :config config}))

(defn my-router
  "Build the FW/1 router from an application (component).
  Provide FW/1's configuration here -- which could come
  from the application component or be placed inline."
  [application]
  (fw1/configure-router {:application     application
                         :application-key (:application-name application)
                         :home            "user.default"}))

(defn my-handler
  "Build the FW/1 handler using a FW/1 router and Compojure
  routes."
  [application]
  (let-routes [fw1 (my-router application)]
    (GET "/" [] (fw1 :user/default))
    (route/resources "/assets" {:root "/usermanager/assets"})
    (GET "/favicon.ico" [] (route/not-found "No favicon.ico"))
    (context "/user" []
             (GET  "/"              [] (fw1 :user/default))
             (GET  "/list"          [] (fw1 :user/list))
             (GET  "/form"          [] (fw1 :user/form))
             (GET  "/form/id/:id"   [] (fw1 :user/form))
             (POST "/save"          [] (fw1 :user/save))
             (GET  "/delete/id/:id" [] (fw1 :user/delete)))))

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

(defn new-system
  "Build a default system to run. In the REPL:

  (def system (new-system 8888))
  (alter-var-root #'system component/start)

  (alter-var-root #'system component/stop)"
  ([port] (new-system port false))
  ([port join?]
   (component/system-map :application (my-application {:port port :repl? (not join?)})
                         :web-server  (web-server port join?))))

(defn -main
  [& [port]]
  (let [port (or port (get (System/getenv) "PORT" 8080))
        port (cond-> port (string? port) Integer/parseInt)]
    (component/start (new-system port true))))
