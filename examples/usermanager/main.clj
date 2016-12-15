(ns usermanager.main
  (:require [com.stuartsierra.component :as component]
            [compojure.coercions :refer :all] ; for as-int
            [compojure.core :refer :all] ; for GET POST and let-routes
            [compojure.route :as route]
            [ring.middleware.defaults :as ring-defaults]
            [ring.util.response :as resp]
            [usermanager.controllers.user :as user-ctl]
            [usermanager.model.user-manager :as model]))

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

(defn my-middleware
  "This middleware runs for every request and can execute before/after logic.
  Note that if 'before' returns an HTTP response, we do not execute the handler
  but after calling the handler, we always call 'after' -- it's up to that
  function to decide what to do if the handler returns an HTTP response."
  [handler]
  (fn [req]
    (let [resp (user-ctl/before req)]
      (if (resp/response? resp)
          resp
          (user-ctl/after (handler req))))))

;; Helper for building the middleware:
(defn- add-app-component
  "Middleware to add your application component into the request. Use
  the same qualified keyword in your controller to retrieve it."
  [handler application]
  (fn [req]
    (handler (assoc req :application/component application))))

;; This is Ring-specific, the specific stack of middleware you need for your
;; application. This example uses a fairly standard stack of Ring middleware
;; with some tweaks for convenience
(defn middleware-stack
  "Given the application component and middleware, return a standard stack of
  Ring middleware for a web application."
  [app-component app-middleware]
  (fn [handler]
    (-> handler
        (app-middleware)
        (add-app-component app-component)
        (ring-defaults/wrap-defaults (-> ring-defaults/site-defaults
                                         ;; disable XSRF for now
                                         (assoc-in [:security :anti-forgery] false)
                                         ;; support load balancers
                                         (assoc-in [:proxy] true))))))


;; This is the main web handler, that builds routing middleware
;; from the application component (defined above). The handler is passed
;; into the web server component (below).
(defn my-handler
  "Given the application component, return middleware for routing."
  [application]
  (-> (let-routes [wrap (middleware-stack application #'my-middleware)]
        (GET  "/"                        []              (wrap #'user-ctl/default))
        ;; horrible: application should POST to this URL!
        (GET  "/user/delete/:id{[0-9]+}" [id :<< as-int] (wrap #'user-ctl/delete-by-id))
        ;; add a new user:
        (GET  "/user/form"               []              (wrap #'user-ctl/edit))
        ;; edit an existing user:
        (GET  "/user/form/:id{[0-9]+}"   [id :<< as-int] (wrap #'user-ctl/edit))
        (GET  "/user/list"               []              (wrap #'user-ctl/get-users))
        (POST "/user/save"               []              (wrap #'user-ctl/save))
        ;; this just resets the change tracker but really should be a POST :)
        (GET  "/reset"                   []              (wrap #'user-ctl/reset-changes))
        (route/resources "/")
        (route/not-found "Not Found"))))

;; Standard web server component -- knows how to stop and start your chosen
;; web server... supports both jetty and http-kit as it stands:
;; lifecycle for the specified web server in which we run
(defrecord WebServer [handler-fn server port ; parameters
                      application            ; dependencies
                      http-server shutdown]  ; state
  component/Lifecycle
  (start [this]
    (if http-server
      this
      (let [start-server (case server
                           :jetty    (do
                                       (require '[ring.adapter.jetty :as jetty])
                                       (resolve 'jetty/run-jetty))
                           :http-kit (do
                                       (require '[org.httpkit.server :as kit])
                                       (resolve 'kit/run-server))
                           (throw (ex-info "Unsupported web server"
                                           {:server server})))]
        (assoc this
               :http-server (start-server (handler-fn application)
                                          (cond-> {:port port}
                                            (= :jetty server)
                                            (assoc :join? false)))
               :shutdown (promise)))))
  (stop  [this]
    (if http-server
      (do
        (case server
          :jetty    (.stop http-server)
          :http-kit (http-server)
          (throw (ex-info "Unsupported web server"
                          {:server server})))
        (assoc this :http-server nil)
        (deliver shutdown true))
      this)))

(defn web-server
  "Return a WebServer component that depends on the application.
  The handler-fn is a function that accepts the application (Component) and
  returns a fully configured Ring handler (with middeware)."
  ([handler-fn port] (web-server handler-fn port :jetty))
  ([handler-fn port server]
   (component/using (map->WebServer {:handler-fn handler-fn
                                     :port port :server server})
                    [:application])))

;; This is the piece that combines the generic web server component above with
;; your application-specific component defined at the top of the file:
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
   (component/system-map :application (my-application {:repl? repl?
                                                       :resources "usermanager"})
                         :web-server  (web-server #'my-handler port server))))

(defn -main
  [& [port server]]
  (let [port (or port (get (System/getenv) "PORT" 8080))
        port (cond-> port (string? port) Integer/parseInt)
        server (or server (get (System/getenv) "SERVER" "jetty"))]
    (println "Starting up on port" port "with server" server)
    (-> (component/start (new-system port (keyword server) false))
        ;; wait for the web server to shutdown
        :web-server :shutdown deref)))
