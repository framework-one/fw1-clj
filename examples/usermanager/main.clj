(ns usermanager.main
  (:require [framework.one :as fw1]
            [usermanager.model.user-manager :as model]
            [com.stuartsierra.component :as component]))

;; Implement your application's lifecycle here:
;; Although the application config is not used in this simple
;; case, it probably would be in the general case -- and the
;; application state here is trivial but could be more complex.
(defrecord Application [config ; configuration (unused)
                        state] ; behavior
  component/Lifecycle
  (start [this]
    ;; set up database if necessary
    (model/setup-database)
    (assoc this :state "Running"))
  (stop  [this]
    (assoc this :state "Stopped")))

(defn my-application
  "Return your application component, fully configured.
  In this simple case, we just pass the whole configuration into
  the application (:repl?)"
  [config]
  (map->Application {:config config}))

;; This is the framework-specific portion, that builds a Ring handler
;; from the application component (defined above). The handler is passed
;; into the web server component (below).
(defn fw1-handler
  "Build the FW/1 handler from the application. This is where you can
  specify the FW/1 configuration and the application routes."
  [application]
  (fw1/default-handler application
                       {:application-key "usermanager"
                        :home            "user.default"}))

(defn new-system
  "Build a default system to run. In the REPL:

  (def system (new-system 8888))
  ;; or
  (def system (new-system 8888 :http-kit))
  (alter-var-root #'system component/start)

  (alter-var-root #'system component/stop)"
  ([port] (new-system port :jetty true))
  ([port server] (new-system port server true))
  ([port server repl?]
   (component/system-map :application (my-application {:repl? repl?})
                         :web-server  (fw1/web-server #'fw1-handler port server))))

(defn -main
  [& [port server]]
  (let [port (or port (get (System/getenv) "PORT" 8080))
        port (cond-> port (string? port) Integer/parseInt)
        server (or server (get (System/getenv) "SERVER" "jetty"))]
    (println "Starting up on port" port "with server" server)
    (-> (component/start (new-system port (keyword server) false))
        ;; wait for the web server to shutdown
        :web-server :shutdown deref)))
