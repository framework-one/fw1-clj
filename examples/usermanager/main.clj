(ns usermanager.main
  (:require [framework.one :as fw1]
            [usermanager.model.user-manager :as model]
            [usermanager.jetty :as jetty]
            [usermanager.http-kit :as http-kit]
            [com.stuartsierra.component :as component]
            [compojure.core :refer :all]
            [compojure.route :as route]))

;; Implement your application's lifecycle here:
;; Although the application config is not used in this simple
;; case, it probably would be in the general case -- and the
;; application state here is trivial but could be more complex.
(defrecord Application [config ; parameters
                        state] ; state
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
  (let-routes [fw1 (fw1/configure-router {:application     application
                                          :application-key "usermanager"
                                          :home            "user.default"})]
    (route/resources "/" {:root "/usermanager"})
    (ANY "/" [] (fw1))
    (context "/:section" [section]
             (ANY "/"             []     (fw1 (keyword section "default")))
             (ANY "/:item"        [item] (fw1 (keyword section item)))
             (ANY "/:item/id/:id" [item] (fw1 (keyword section item))))))

(defn new-system
  "Build a default system to run. In the REPL:

  (def system (new-system 8888))
  (alter-var-root #'system component/start)

  (alter-var-root #'system component/stop)"
  ([port] (new-system port :jetty false))
  ([port server] (new-system port server false))
  ([port server join?]
   (let [start-server (case server
                        :jetty jetty/web-server
                        :http-kit http-kit/web-server
                        (throw (ex-info "Unknown web server" {:server server})))]
     (component/system-map :application (my-application {:repl? (not join?)})
                           :web-server  (start-server #'fw1-handler port join?)))))

(defn -main
  [& [port server]]
  (let [port (or port (get (System/getenv) "PORT" 8080))
        port (cond-> port (string? port) Integer/parseInt)
        server (or server (get (System/getenv) "SERVER" "jetty"))]
    (println "Starting up on port" port "with server" server)
    (component/start (new-system port (keyword server) true))))
