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
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str]
            [compojure.coercions :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [framework.one.request :as req]
            [framework.one.response :as resp]
            [framework.one.server :as server]
            [framework.one.view-layout :as view]
            [ring.middleware.defaults :as ring-md]
            [ring.middleware.json :as ring-json]
            [selmer.filters]
            [selmer.parser]))

;; bridge in a couple of very useful Selmer symbols - this lets you use
;; fw1/add-tag! and fw1/add-filter! in your application, rather than reach
;; into Selmer itself

(intern *ns* (with-meta 'add-tag!
               (meta #'selmer.parser/add-tag!))
        (deref #'selmer.parser/add-tag!))
(intern *ns* (with-meta 'add-filter!
               (meta #'selmer.filters/add-filter!))
        (deref #'selmer.filters/add-filter!))

;; FW/1 base functionality - this is essentially the public API of the
;; framework with the entry point to create Ring middleware being:
;; (fw1/default-handler) - returns Ring middleware for your application
;; See the bottom of this file for more details

(declare ring)

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

(defn redirecting?
  "Return true if a redirect is in progress."
  [rc]
  (contains? rc ::redirect))

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
  "Return the remote IP address for this request."
  [rc]
  (req/remote-addr (cond-> rc (req/legacy? rc) ring)))

(defn render-data
  "Tell FW/1 to render this expression as the given type.
  Prefer the convenience functions below, unless you are rendering
  custom data types."
  ([rc as expr]
   (assoc rc ::render {:as as :data expr}))
  ([rc status as expr]
   (assoc rc ::render {:status status :as as :data expr})))

(defn render-by
  "Given a rendering function, return a convenience function that
  accepts the rc, the optional status code to return, and the expression
  to render."
  [render-fn]
  (fn
    ([rc expr]        (render-data rc render-fn expr))
    ([rc status expr] (render-data rc status render-fn expr))))

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

(defn rendering?
  "Return true if a render is in progress."
  [rc]
  (contains? rc ::render))

(defn ring
  "Get data from the original Ring request -- not really intended for
  public usage, but may be useful to some applications.
  (ring rc) - returns the whole Ring request
  (ring rc req) - set the Ring request data (useful for building test data)"
  ([rc] (get rc ::ring))
  ([rc req] (assoc rc ::ring req)))

(def servlet-request req/servlet-request)

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

(def ->fs view/->fs)

(defn as-map
  "Given the remaining expanded part of the route (after the section and item),
  return it as a map of parameters:
  /foo/bar/baz/quux/fie/foe -> section foo, item bar, and {baz quux, fie foo}
  NOTE: not used inside FW/1!"
  [route]
  (apply hash-map
         (if (even? (count route))
           route
           (concat route [""]))))

(def stem view/stem)

;; main FW/1 logic

(def get-view-path view/get-view-path)

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

(def get-layout-paths view/get-layout-paths)

(def apply-view view/apply-view)

(def apply-layout view/apply-layout)

(def html-response view/html-response)

(def not-found view/not-found)

(def render-page
  "Given the application configuration, the request context, the section and item, and a flag that
  indicates whether we're already processing an exception, try to render a view and a cascade of
  available layouts. The result will either be a successful HTML page or a 500 error."
  view/render-page)

(def render-data-response resp/render-data-response)

(defn section->controller
  "Given a config and a section, return the controller namespace."
  [config section]
  (symbol (->clj (str (stem config ".") "controllers." section))))

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
        controller-ns (section->controller config section)
        _ (when (:lazy-load config) (require-controller rc controller-ns))
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
               "Access-Control-Allow-Credentials" (str (:credentials access-control))
               "Access-Control-Max-Age"           (str (:max-age access-control))}}))

(defn- default-middleware
  "The default Ring middleware we apply in FW/1. Returns a single
  composed piece of middleware. We start with Ring's site defaults
  and the middleware-default-fn may modify those defaults. Then we
  wrapper the handler in one last optional piece of middleware."
  (let [{:keys [middleware-default-fn middleware-wrapper-fn]
         :or   {middleware-default-fn identity
                middleware-wrapper-fn identity}} config]
    (fn [handler]
      (-> handler
          (ring-md/wrap-defaults (-> ring-md/site-defaults
                                          ; you have to explicitly opt in to this:
                                     (assoc-in [:security :anti-forgery] false)
                                     (assoc-in [:proxy] true)
                                     (middleware-default-fn)))
          (ring-json/wrap-json-params)
          (ring-json/wrap-json-response (:json-config config))
          (middleware-wrapper-fn)))))

(def ^:private default-options-access-control
  {:origin      "*"
   :headers     "Accept,Authorization,Content-Type"
   :credentials true
   :max-age     1728000})

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
         :middleware (default-middleware config)
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
                   (assoc :uri (str "/" section "/" item)
                                        ; ensure error handled as GET
                          :request-method :get)
                   (assoc-in [:params :exception] e))))))))

(defn- wildcard-filter
  "Given a regex, return a FilenameFilter that matches."
  [re]
  (reify java.io.FilenameFilter
    (accept [_ dir name] (not (nil? (re-find re name))))))

(defn jar-file-list
  "Given a JAR file and a regex, return a seq of matching filenames."
  [jar-file re]
  (->> jar-file
       (java.util.jar.JarFile.)
       (.entries)
       (iterator-seq)
       (filter #(re-find re (.getName %)))
       (map #(.getName %))))

(defn path-file-list
  "Given a class path element (probably a jar file) and a regex, return a
  sequence of matching filenames."
  [path re]
  (when (str/ends-with? path ".jar")
    (jar-file-list (io/file path) re)))

(defn class-path-list
  "Given a regex, look in all the class path files for matching files."
  [re]
  (mapcat #(path-file-list % re)
          (seq (.split (System/getProperty "java.class.path")
                       (System/getProperty "path.separator")))))

(defn directory-list
  "Given a directory and a regex, return a sorted seq of matching filenames."
  [dir re]
  (let [r (io/resource dir)]
    (if (= "file" (.getProtocol r))
      (sort (.list (io/file r) (wildcard-filter re)))
      (let [dir-re (re-pattern (str "^" dir "/.*" re))
            skip   (inc (count dir))]
        (sort (map #(subs % skip) (class-path-list dir-re)))))))

(defn configure-router
  "Given the application configuration, return a router function that takes a
  :section/item keyword and produces a function that handles a (Ring) request."
  [app-config]
  (let [config (build-config app-config)]
    (when-not (:lazy-load config)
      (doseq [c (directory-list (str (stem config "/") "controllers") #"\.clj")]
        (require-controller {} (section->controller config
                                                    (str/replace c ".clj" "")))))
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
                  (fw1 (keyword section item))))
    (route/not-found "Not Found")))

(def web-server server/web-server)
