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
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.flash :as ring-f]
            [ring.middleware.params :as ring-p]
            [ring.middleware.resource :as ring-r]
            [ring.middleware.session :as ring-s]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.session.memory :refer [memory-store]]
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

;; scope access utility
(defn scope-access [scope]
  (fn
    ([rc n] (get-in rc [::request scope n]))
    ([rc n v] (assoc-in rc [::request scope n] v))))

;; render data support
(defn render-data
  ([rc as expr]
   (assoc rc ::render {:as as :data expr}))
  ([rc status as expr]
   (assoc rc ::render {:status status :as as :data expr})))

;; FW/1 base functionality - this is essentially the public API of the
;; framework with the entry point to create Ring middleware being:
;; (fw1/start) - returns Ring middleware for your application
;; See the bottom of this file for more details

(def cookie
  "Get / set items in cookie scope:
  (cookie rc name) - returns the named cookie
  (cookie rc name value) - sets the named cookie"
  (scope-access :cookies))

(def event
  "Get / set FW/1's 'event' scope data. Valid event scope entries are:
  :action :section :item :config
  You should normally only read the event data: (event rc key)"
  (scope-access ::event))

(def flash
  "Get / set items in 'flash' scope. Data stored in flash scope in
  a request should be automatically restored to the 'rc' on the
  subsequent request. You should not need to read flash scope,
  just store items there: (flash rc name value)"
  (scope-access :flash))

(defn header
  "Either read the request headers or write the response headers:
  (header rc name) - return the named (request) header
  (header rc name value) - write the named (response) header"
  ([rc n] (get-in rc [::request :req-headers n]))
  ([rc n v] (assoc-in rc [::request :headers n] v)))

(defn redirect
  "Tell FW/1 to perform a redirect."
  [rc url]
  (assoc rc ::redirect {:status 302 :headers {"Location" url}}))

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
  This value comes directly from Ring and is dependent on your
  application server (so it may be IPv4 or IPv6)."
  [rc]
  (get-in rc [::request :remote-addr]))

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

(defn servlet-request
  "Return a fake HttpServletRequest that knows how to delegate to the rc."
  [rc]
  (proxy [javax.servlet.http.HttpServletRequest] []
    (getParameter [name]
      (if-let [v (get rc (keyword name))] (str v) nil))))

(def session
  "Get / set items in session scope:
  (session rc name) - returns the named session variable
  (session rc value) - sets the named session variable
  Session variables persist across requests and use Ring's session
  middleware (and can be memory or cookie-based at the moment)."
  (scope-access :session))

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
  (if (or (::redirect rc)
          (::render rc))
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
  {:html {:type "text/html; charset=utf-8"
          :body (fn [config data] data)}
   :json {:type "application/json; charset=utf-8"
          :body (fn [config data]
                  (if-let [json-config (:json-config config)]
                    (json/generate-string data json-config)
                    (json/generate-string data)))}
   :text {:type "text/plain; charset=utf-8"
          :body (fn [config data] data)}
   :xml  {:type "text/xml; charset=utf-8"
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
  "Given a request context and a Ring request, return the request context with certain
  Ring data embedded in it. In particular, we keep request headers separate to any
  response headers (and merge those in unpack-response below)."
  [rc req]
  (merge
   (reduce (fn [m k]
             (assoc-in m
                       [::request (if (= :headers k) :req-headers k)]
                       (or (k req) {})))
           rc
           [:session :cookies :remote-addr :headers])
   (:flash req)))

(defn unpack-response
  "Given a request context and a response, return the response with Ring data added.
  By this point the response always has headers so we must add to those, not overwrite."
  [rc resp]
  (reduce (fn [m k]
            (if (= :headers k)
              (update m k merge (get-in rc [::request k]))
              (assoc m k (get-in rc [::request k]))))
          resp
          [:session :cookies :flash :headers]))

(defn adjust-rc-by-status
  "Given the 'rc', a (possibly nil) HTTP status, and a new route,
  modify the 'rc' to indicate a redirect or a render if appropriate."
  [rc status route]
  (case status
    (301 302) (redirect rc route)
    403       (render-html rc 403 "Forbidden")
    404       (render-html rc 404 "Not Found")
    405       (render-html rc 405 "Method Not Allowed")
    rc))

(defn render-request
  "Given the application configuration, the specific section, item, HTTP status,
  the trailing routes pieces, and a (Ring) request, convert that to a FW/1
  request context 'rc' and locate and run the controller, then either redirect,
  render an expression as data, or try to render views and layouts."
  ([config section item req]
   (render-request config section item 200 nil req))
  ([config section item status route req]
   (let [exceptional? (::handling-exception req)
         rc (-> (walk/keywordize-keys (merge (as-map (rest (rest route))) (:params req)))
                (pack-request req)
                (event :action  (str section "." item))
                (event :section section)
                (event :item    item)
                (event :config  config)
                (adjust-rc-by-status status route))
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
          (unpack-response rc)))))

(defn render-options
  "Given the application configuration and a Ring request, return an OPTIONS response."
  [config req]
  (let [access-control (:options-access-control config)
        methods        "OPTIONS,GET,POST,PUT,PATCH,DELETE"] ; should be dynamic, also include HEAD?
    {:status  200
     :body    ""
     :headers [{"Content-Type"                     "text/plain; charset=utf-8"}
               {"Access-Control-Allow-Origin"      (:origin access-control)}
               {"Access-Control-Allow-Methods"     methods}
               {"Access-Control-Allow-Headers"     (:headers access-control)}
               {"Access-Control-Allow-Credentials" (:credentials access-control)}
               {"Access-Control-Max-Age"           (str (:max-age access-control))}]}))

(defn- default-middleware
  "The default set of Ring middleware we apply in FW/1"
  [config]
  [ring-p/wrap-params
   ring-f/wrap-flash
   (fn [h]
    (condp = (:session-store config)
      :memory (ring-s/wrap-session h {:store (memory-store)})
      :cookie (ring-s/wrap-session h {:store (cookie-store)})
      (ring-s/wrap-session h)))
   (fn [req] (ring-r/wrap-resource req (stem config "/")))])

(defn- merge-middleware
  "Return a function that, given any user-supplied middleware (as a vector),
  will combine that will our default Ring middleware (see above). The user
  supplied middleware vector is prepended before the default middleware by
  default, but can have a placement as its first element:
  - :append  - append supplied middleware after defaults
  - :replace - use supplied middleware instead of defaults
  - :prepend - prepend supplied middleware before defaults"
  [config]
  (fn [middleware]
    (if middleware
      (condp = (first middleware)
        :append (concat (default-middleware config) (rest middleware))
        :replace (rest middleware)
        :prepend (concat (rest middleware) (default-middleware config))
        (concat middleware (default-middleware config)))
      (default-middleware config))))

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
   :suffix "html" ; views / layouts would be .html
   :version "0.6.0"})

(defn- build-config
  "Given a 'public' application configuration, return the fully built
  FW/1 configuration for it."
  [app-config]
  (let [config (framework-defaults (merge default-options app-config))]
    (update config :middleware (merge-middleware config))))

(defn configure-router
  "Given the application configuration, return a function that takes a
  :section/item keyword and produces a function that handles a (Ring) request."
  [app-config]
  (let [config  (build-config app-config)]
    (fn configured-fw1
      ([] (configured-fw1 (keyword (first  (:home config))
                                   (second (:home config)))))
      ([section-item] ; :section or :section/item
       (let [[section item] (if (namespace section-item)
                              [(namespace section-item) (name section-item)]
                              [(name section-item) (second (:home config))])]
         (reduce (fn [handler middleware] (middleware handler))
                 (fn [req]
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
                               item    (second (:error config))]
                           ((configured-fw1 (keyword section item))
                            (-> req
                                (assoc ::handling-exception true)
                                (assoc :uri (str "/" section "/" item))
                                (assoc-in [:params :exception] e))))))))
                 (:middleware config)))))))

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
             (ANY "/:item/:id{[0-9]+}" [item] (fw1 (keyword section item))))))
