(ns usermanager.http-kit
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]))


;; lifecycle for the Jetty server in which we run
(defrecord WebServer [handler-fn port join? ; parameters
                      application           ; dependencies
                      http-server]          ; state
  component/Lifecycle
  (start [this]
    (if (:http-server this)
      this
      (assoc this :http-server (let [server (run-server (handler-fn application)
                                                        {:port port})]
                                        ; http-kit doesn't support join?
                                 (when join?
                                   @(promise))
                                 server))))
  (stop  [this]
    (if (:http-server this)
      (do
        ((:http-server this))
        (assoc this :http-server nil))
      this)))

(defn web-server
  "Return a WebServer component that depends on the application."
  [handler-fn port join?]
  (component/using (map->WebServer {:handler-fn handler-fn
                                    :port port :join? join?})
                   [:application]))
