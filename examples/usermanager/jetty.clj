(ns usermanager.jetty
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :refer [run-jetty]]))


;; lifecycle for the Jetty server in which we run
(defrecord WebServer [handler-fn port join? ; parameters
                      application           ; dependencies
                      http-server]          ; state
  component/Lifecycle
  (start [this]
    (if (:http-server this)
      this
      (assoc this :http-server (run-jetty (handler-fn application)
                                          {:port port :join? join?}))))
  (stop  [this]
    (if (:http-server this)
      (do
        (.stop (:http-server this))
        (assoc this :http-server nil))
      this)))

(defn web-server
  "Return a WebServer component that depends on the application."
  [handler-fn port join?]
  (component/using (map->WebServer {:handler-fn handler-fn
                                    :port port :join? join?})
                   [:application]))
