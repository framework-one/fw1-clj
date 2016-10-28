;; Framework One (FW/1) Copyright (c) 2012-2016 Sean Corfield
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

(ns framework.one
  (:require [cheshire.core :as json]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [compojure.coercions :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :as ring-md]
            [selmer.filters]
            [selmer.parser]
            [selmer.util :refer [resource-path]]))

;; bridge in a couple of very useful Selmer symbols - this lets you use
;; fw1/add-tag! and fw1/add-filter! in your application, rather than reach
;; into Selmer itself

(intern *ns* (with-meta 'add-tag!
               (meta #'selmer.parser/add-tag!))
        (deref #'selmer.parser/add-tag!))
(intern *ns* (with-meta 'add-filter!
               (meta #'selmer.filters/add-filter!))
        (deref #'selmer.filters/add-filter!))

;; render data support
(defn render-data
  ([rc as expr]
   (assoc rc ::render {:as as :data expr}))
  ([rc status as expr]
   (assoc rc ::render {:status status :as as :data expr})))

;; FW/1 base functionality - this is essentially the public API of the
;; framework with the entry point to create Ring middleware being:
;; (fw1/default-handler) - returns Ring middleware for your application
;; See the bottom of this file for more details

(defn abort
  "Abort the controller lifecycle."
  [rc]
  (assoc rc ::abort ::controller))

(defn cookie
  "Get / set items in cookie scope:
  (cookie rc name) - returns the named cookie
  (cookie rc name value) - sets the named cookie"
  ([rc name] (get-in rc [::ring :cookies name]))
  ([rc name value] (assoc-in rc [::ring :cookies name] value)))

(defn event
  "Get / set FW/1's 'event' scope data. Valid event scope entries are:
  :action :section :item :config and :headers (response headers)
  (event rc name) - returns the named event data
  (event rc name value) - sets the named event data (internal use only!)"
  ([rc name] (get-in rc [::event name]))
  ([rc name value] (assoc-in rc [::event name] value)))

(defn flash
  "Get / set items in 'flash' scope. Data stored in flash scope in
  a request should be automatically restored to the 'rc' on the
  subsequent request.
  (flash rc name value)"
  ([rc name value] (assoc-in rc [::ring :flash name] value)))

(defn header
  "Either read the request headers or write the response headers:
  (header rc name) - return the named (request) header
  (header rc name value) - write the named (response) header"
  ([rc n] (get-in rc [::ring :headers n]))
  ([rc n v] (assoc-in rc [::event :headers n] v)))

(defn parameters
  "Return just the parameters portion of the request context, without
  the Ring request and event data special keys. This should be used
  when you need to iterate over the form/URL scope elements of the
  request context without needing to worry about special keys."
  [rc]
  (dissoc rc ::abort ::event ::redirect ::render ::ring))

(defn redirect
  "Tell FW/1 to perform a redirect."
  ([rc url] (redirect rc 302 url))
  ([rc status url]
   (assoc rc ::redirect {:status status :headers {"Location" url}})))

(defn reload?
  "Returns true if the current request is a reload request (or
  the application is configured to reload on every request)."
  [rc]
  (let [config (event rc :config)
        reload (get rc (:reload config))
        password (:password config)]
    (or (and reload password (= reload password))
        (:reload-application-on-every-request config))))

(defn remote-addr
  "Return the remote IP address for this request.
  We attempt to deal with common load balancers that provide the
  x-forwarded-for header and read that first, else fall back to
  the value that Ring got from the application server.
  Note that the result may be an IPv4 or IPv6 value (and may, in
  fact, contain additional characters -- you'll need to clean it
  yourself)."
  [rc]
  (or (get-in rc [::ring :headers "x-forwarded-for"])
      (get-in rc [::ring :remote-addr])))

(defn render-html
  "Tell FW/1 to render this expression (string) as-is as HTML."
  ([rc expr]
   (render-data rc :html expr))
  ([rc status expr]
   (render-data rc status :html expr)))

(defn render-json
  "Tell FW/1 to render this expression as JSON."
  ([rc expr]
   (render-data rc :json expr))
  ([rc status expr]
   (render-data rc status :json expr)))

(defn render-raw-json
  "Tell FW/1 to render this string as raw (encoded) JSON."
  ([rc expr]
   (render-data rc :raw-json expr))
  ([rc status expr]
   (render-data rc status :raw-json expr)))

(defn render-text
  "Tell FW/1 to render this expression (string) as plain text."
  ([rc expr]
   (render-data rc :text expr))
  ([rc status expr]
   (render-data rc status :text expr)))

(defn render-xml
  "Tell FW/1 to render this expression as XML.
  Uses clojure.data.xml's sexp-as-element - see those docs for more detail."
  ([rc expr]
   (render-data rc :xml expr))
  ([rc status expr]
   (render-data rc status :xml expr)))

(defn ring
  "Get data from the original Ring request -- not really intended for
  public usage, but may be useful to some applications.
  (ring rc) - returns the whole Ring request
  (ring rc req) - set the Ring request data (useful for building test data)"
  ([rc] (get rc ::ring))
  ([rc req] (assoc rc ::ring req)))

(defn servlet-request
  "Return a fake HttpServletRequest that knows how to delegate to the rc."
  [rc]
  (proxy [javax.servlet.http.HttpServletRequest] []
    (getContentType []
      (get-in (ring rc) [:headers "content-type"]))
    (getHeader [name]
      (get-in (ring rc) [:headers (str/lower-case name)]))
    (getMethod []
      (-> (ring rc) :request-method name str/upper-case))
    (getParameter [name]
      (if-let [v (get rc (keyword name))] (str v) nil))))

(defn session
  "Get / set items in session scope:
  (session rc name) - returns the named session variable
  (session rc name value) - sets the named session variable
  Session variables persist across requests and use Ring's session
  middleware (and can be memory or cookie-based at the moment)."
  ([rc name] (get-in rc [::ring :session name]))
  ([rc name value] (assoc-in rc [::ring :session name] value)))

(defn to-long
  "Given a string, convert it to a long (or zero if it is not
  numeric). This provides a quick'n'dirty way to process integral
  values passed in the 'rc'. For more sophisticated parsing, or
  for other data types, you'll need to roll your own conversions."
  [l]
  (try (Long/parseLong l) (catch Exception _ 0)))

;; FW/1 implementation

;; utilities for handling file paths

(defn ->clj
  "Given a filesystem path, return a Clojure ns/symbol. This just follows the
  convention that a _ in a filename becomes a - in a namespace/function."
  [path]
  (.replaceAll path "_" "-"))

(defn ->fs
  "Given a Clojure ns/symbol, return a filesystem path. This just follows the
  convention that a - in a namespace/function becomes a _ in a filename."
  [path]
  (.replaceAll path "-" "_"))

(defn as-map
  "Given the remaining expanded part of the route (after the section and item),
  return it as a map of parameters:
  /foo/bar/baz/quux/fie/foe -> section foo, item bar, and {baz quux, fie foo}"
  [route]
  (apply hash-map
         (if (even? (count route))
           route
           (concat route [""]))))

(defn stem
  "Given the application configuration and a separator, return the stem of a path
  to the controllers, layouts, views or (Ring) resources. Returns an empty string
  unless :application-key is provided in the configuration. The :application-key
  may specify a single folder name relative to the classpath, in which to find
  the application source code (this allows multiple FW/1 applications to be run
  in the same classpath without conflict)."
  [config sep]
  (if-let [app (:application-key config)]
    (str app sep)
    ""))

;; main FW/1 logic

(defn get-view-path
  "Given the application configuration, and the section and item, return the path to
  the matching view (which may or may not exist)."
  [config section item]
  (->fs (str (stem config "/") "views/" section "/" item "." (:suffix config))))

(defn apply-controller
  "Given the application configuration, a controller namespace, the request context, and
  the item for a request, return the new request context with the controller applied.
  It the item is a keyword, it is assumed to refer to a controller-like function in the
  application configuration (specifically :before or :after).
  If the request context indicates that a redirect or a data render is desired, do not
  apply any further controller functions."
  [config controller-ns rc item]
  (if (::abort rc)
    rc
    (if (keyword? item)
      (if-let [f (item config)] (f rc) rc)
      (if-let [f (resolve (symbol (str controller-ns "/" (->clj item))))] (f rc) rc))))

(defn get-layout-paths
  "Given the application configuration, and the section and item, return the sequence of
  applicable (and existing!) layouts."
  [config section item]
  (let [dot-html (str "." (:suffix config))]
    (filter resource-path
            (map ->fs [(str (stem config "/") "layouts/" section "/" item dot-html)
                       (str (stem config "/") "layouts/" section dot-html)
                       (str (stem config "/") "layouts/default" dot-html)]))))

(defn apply-view
  "Given the application configuration, the request context, the sction and item, and a flag that
  indicates whether we're already processing an exception, attempt to render the matching view."
  [config rc section item exceptional?]
  (let [view-path   (get-view-path config section item)
        render-view (fn [] (selmer.parser/render-file view-path rc (:selmer-tags config)))]
    (when (resource-path view-path)
      (if exceptional?
        ;; if we fail to render a view while processing an exception
        ;; just treat the (error) view as not found so the original
        ;; exception will be returned as a 500 error
        (try (render-view) (catch Exception _))
        (render-view)))))

(defn apply-layout
  "Given the application configuration, the request context, a flag that indicates
  whether we're already processing an exception, the view HTML so far and the
  desired layout path, attempt to render that layout."
  [config rc exceptional? html layout-path]
  (let [render-layout (fn [] (selmer.parser/render-file layout-path
                                                        (assoc rc :body [:safe html])
                                                        (:selmer-tags config)))]
    (if exceptional?
      ;; if we fail to render a layout while processing an exception
      ;; just treat the layout as not found so the original view will
      ;; be returned unadorned
      (try (render-layout) (catch Exception _ html))
      (render-layout))))

(defn html-response
  "Convenience method to return an HTML response (with a status and body)."
  [status body]
  {:status  status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    body})

(defn not-found
  "Return a basic 404 Not Found page."
  []
  (html-response 404 "Not Found"))

(defn render-page
  "Given the application configuration, the request context, the section and item, and a flag that
  indicates whether we're already processing an exception, try to render a view and a cascade of
  available layouts. The result will either be a successful HTML page or a 500 error."
  [config rc section item exceptional?]
  (if-let [view-render (apply-view config rc section item exceptional?)]
    (let [layout-cascade (get-layout-paths config section item)
          final-html (reduce (partial apply-layout config rc exceptional?) view-render layout-cascade)]
      (html-response 200 final-html))
    (if exceptional?
      (html-response 500 (if-let [e (:exception rc)] (str e) "Unknown Error"))
      (not-found))))

(defn as-xml
  "Given an expression, return an XML string representation of it."
  [expr]
  (with-out-str (xml/emit (xml/sexp-as-element expr) *out*)))

(def ^:private render-types
  "Supported content types and renderers."
  {:html     {:type "text/html; charset=utf-8"
              :body (fn [config data] data)}
   :json     {:type "application/json; charset=utf-8"
              :body (fn [config data]
                      (if-let [json-config (:json-config config)]
                        (json/generate-string data json-config)
                        (json/generate-string data)))}
   :raw-json {:type "application/json; charset=utf-8"
              :body (fn [config data] data)}
   :text     {:type "text/plain; charset=utf-8"
              :body (fn [config data] data)}
   :xml      {:type "text/xml; charset=utf-8"
              :body (fn [config data] (as-xml data))}})

(defn render-data-response
  "Given the format and data, return a success response with the appropriate
  content type and the data rendered as the body."
  [config {:keys [status as data]
           :or   {status 200}}]
  (let [renderer (render-types as)]
    {:status  status
     :headers {"Content-Type" (:type renderer)}
     :body    ((:body renderer) config data)}))

(defn require-controller
  "Given the request context and a controller namespace, require it.
  This is where we optionally force a reload of the Clojure code.
  We require on every request. Since Clojure prefers in-memory classes, this is
  safe and performant: once it has loaded a namespace, it will not look on disk."
  [rc controller-ns]
  (try
    (if (reload? rc)
      (require controller-ns :reload)
      (require controller-ns))
    (catch java.io.FileNotFoundException _
      ;; missing controller OK; anything else should bubble up
      nil)))

(defn pack-request
  "Given a request context and a Ring request, return the request context with
  the Ring data embedded in it, and the 'flash' scope merged."
  [rc req]
  (merge (ring rc req) (:flash req)))

(defn unpack-response
  "Given a request context (returned by controllers) and a response map (status,
  headers, body), return a full Ring response map."
  [rc resp]
  (merge (ring rc) (update resp :headers merge (event rc :headers))))

(defn render-request
  "Given the application configuration, the specific section, item, and a (Ring)
  request, convert that to a FW/1 request context 'rc' and locate and run the
  controller, then either redirect,render an expression as data, or try to
  render views and layouts."
  [config section item req]
  (let [exceptional? (::handling-exception req)
        rc (-> (:params req)
               (pack-request req)
               (event :action  (str section "." item))
               (event :section section)
               (event :item    item)
               (event :config  config))
        controller-ns (symbol (->clj (str (stem config ".") "controllers." section)))
        _ (require-controller rc controller-ns)
        rc (reduce (partial apply-controller config controller-ns)
                   rc
                   [:before "before" item "after" :after])]
    (->> (if-let [redirect (::redirect rc)]
           redirect
           (if-let [render-expr (::render rc)]
             (render-data-response config render-expr)
             (render-page config rc section item exceptional?)))
         (unpack-response rc))))

(defn render-options
  "Given the application configuration and a Ring request, return an OPTIONS response."
  [config req]
  (let [access-control (:options-access-control config)
        methods        "OPTIONS,GET,POST,PUT,PATCH,DELETE"] ; should be dynamic, also include HEAD?
    {:status  200
     :body    ""
     :headers {"Content-Type"                     "text/plain; charset=utf-8"
               "Access-Control-Allow-Origin"      (:origin access-control)
               "Access-Control-Allow-Methods"     methods
               "Access-Control-Allow-Headers"     (:headers access-control)
               "Access-Control-Allow-Credentials" (:credentials access-control)
               "Access-Control-Max-Age"           (str (:max-age access-control))}}))

(defn- default-middleware
  "The default Ring middleware we apply in FW/1. Returns a single
  composed piece of middleware. We start with Ring's site defaults
  and the fn passed in may modify those defaults."
  [modifier-fn]
  (fn [handler]
    (ring-md/wrap-defaults handler (-> ring-md/site-defaults
                                        ; you have to explicitly opt in to this:
                                       (assoc-in [:security :anti-forgery] false)
                                       modifier-fn))))

(def ^:private default-options-access-control
  {:origin      "*"
   :headers     "Accept,Authorization,Content-Type"
   :credentials true
   :max-age     1728000} )

(defn- framework-defaults
  "Calculate configuration items based on supplied options or defaults."
  [options]
  (assoc options
         :error (if (:error options)
                  (clojure.string/split (:error options) #"\.")
                  [(:default-section options) "error"])
         :home  (if (:home options)
                  (clojure.string/split (:home options) #"\.")
                  [(:default-section options) (:default-item options)])
                                        ; can modify site-defaults
         :middleware (default-middleware (or (:middleware-default-fn options)
                                             identity))
         :options-access-control (merge default-options-access-control
                                        (:options-access-control options))))

(def ^:private default-options
  {:after identity
   :before identity
   :default-item "default"
   :default-section "main"
   :password "secret"
   :reload :reload
   :reload-application-on-every-request false
   :suffix "html"})

(defn- build-config
  "Given a 'public' application configuration, return the fully built
  FW/1 configuration for it."
  [app-config]
  (let [version (str/replace (slurp (io/resource "fw1.version")) "\n" "")]
    (framework-defaults (merge default-options
                               {:version version}
                               app-config))))

(defn- handler
  "The underlying Ring request handler for FW/1."
  [config section item req fw1-router]
  (try
    (if (= :options (:request-method req))
      (render-options config req)
      (render-request config section item req))
    (catch Exception e
      (if (::handling-exception req)
        (do
          (stacktrace/print-stack-trace e)
          (html-response 500 (str e)))
        (let [section (first  (:error config))
              item    (second (:error config))
              fw1     (fw1-router (keyword section item))]
          (fw1 (-> req
                   (assoc ::handling-exception true)
                   (assoc :uri (str "/" section "/" item))
                   (assoc-in [:params :exception] e))))))))

(defn configure-router
  "Given the application configuration, return a router function that takes a
  :section/item keyword and produces a function that handles a (Ring) request."
  [app-config]
  (let [config (build-config app-config)]
    (fn fw1-router
      ([] (fw1-router (keyword (first  (:home config))
                               (second (:home config)))))
      ([section-item] ; :section or :section/item
       (let [[section item] (if (namespace section-item)
                              [(namespace section-item) (name section-item)]
                              [(name section-item) (second (:home config))])
             middleware-fn  (:middleware config)]
         (middleware-fn (fn [req]
                          (handler config section item req fw1-router))))))))

(defn default-handler
  "Build a default FW/1 handler from the application and configuration.
  This uses a basic set of routes that should provide roughly the same
  default behavior as the previous (0.6.0) convention-based routes."
  [application config]
  (let-routes [fw1 (configure-router (assoc config :application application))]
    (route/resources "/")
    (ANY "/" [] (fw1))
    (context "/:section" [section]
             (ANY "/"                  []     (fw1 (keyword section)))
             (ANY "/:item"             [item] (fw1 (keyword section item)))
             (ANY "/:item/:id{[0-9]+}" [item id :<< as-int]
                  (fw1 (keyword section item))))))

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
    (if (:http-server this)
      this
      (let [start-server (case (:server this)
                           :jetty    (do
                                       (require '[ring.adapter.jetty :as jetty])
                                       (resolve 'jetty/run-jetty))
                           :http-kit (do
                                       (require '[org.httpkit.server :as kit])
                                       (resolve 'kit/run-server))
                           (throw (ex-info "Unsupported web server"
                                           {:server (:server this)})))]
        (assoc this
               :http-server (start-server ((:handler-fn this) application)
                                          (cond-> {:port (:port this)}
                                            (= :jetty (:server this))
                                            (assoc :join? false)))
               :shutdown (promise)))))
  (stop  [this]
    (if (:http-server this)
      (do
        (case (:server this)
          :jetty    (.stop (:http-server this))
          :http-kit ((:http-server this))
          (throw (ex-info "Unsupported web server"
                          {:server (:server this)})))
        (assoc this :http-server nil)
        (deliver (:shutdown this) true))
      this)))

(defn web-server
  "Return a WebServer component that depends on the application."
  ([handler-fn port] (web-server handler-fn port :jetty))
  ([handler-fn port server]
   (component/using (map->WebServer {:handler-fn handler-fn
                                     :port port :server server})
                    [:application])))
