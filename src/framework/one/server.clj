;; Framework One (FW/1) Copyright (c) 2016 Sean Corfield
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;   http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns framework.one.server
  (:require [com.stuartsierra.component :as component]))

;; As of 2016/10/27, these are the two sets of options for the web servers that
;; we support -- note that only :port is common between the two of them.
;; We probably ought to open up more of the server's options to the caller,
;; perhaps replacing port with a full blown options map. At the very least we
;; probably should support :ip / :host and maybe threads.

(comment
  "http-kit options:

    :ip                 ; Which ip (if has many ips) to bind
    :port               ; Which port listen incomming request
    :thread             ; Http worker thread count
    :queue-size         ; Max job queued before reject to project self
    :max-body           ; Max http body: 8m
    :max-ws             ; Max websocket message size
    :max-line           ; Max http inital line length
    :proxy-protocol     ; Proxy protocol e/o #{:disable :enable :optional}
    :worker-name-prefix ; Woker thread name prefix
    :worker-pool        ; ExecutorService to use for request-handling (:thread,
                          :worker-name-prefix, :queue-size are ignored if set)
")

(comment
  "Jetty options:

  :configurator         - a function called with the Jetty Server instance
  :async?               - if true, treat the handler as asynchronous
  :port                 - the port to listen on (defaults to 80)
  :host                 - the hostname to listen on
  :join?                - blocks the thread until server ends (defaults to true)
  :daemon?              - use daemon threads (defaults to false)
  :http?                - listen on :port for HTTP traffic (defaults to true)
  :ssl?                 - allow connections over HTTPS
  :ssl-port             - the SSL port to listen on (defaults to 443, implies
                          :ssl? is true)
  :exclude-ciphers      - When :ssl? is true, exclude these cipher suites
  :exclude-protocols    - When :ssl? is true, exclude these protocols
  :keystore             - the keystore to use for SSL connections
  :key-password         - the password to the keystore
  :truststore           - a truststore to use for SSL connections
  :trust-password       - the password to the truststore
  :max-threads          - the maximum number of threads to use (default 50)
  :min-threads          - the minimum number of threads to use (default 8)
  :max-idle-time        - the maximum idle time in milliseconds for a connection
                          (default 200000)
  :client-auth          - SSL client certificate authenticate, may be set to
                          :need,:want or :none (defaults to :none)
  :send-date-header?    - add a date header to the response (default true)
  :output-buffer-size   - the response body buffer size (default 32768)
  :request-header-size  - the maximum size of a request header (default 8192)
  :response-header-size - the maximum size of a response header (default 8192)
  :send-server-version? - add Server header to HTTP response (default true)
")

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
