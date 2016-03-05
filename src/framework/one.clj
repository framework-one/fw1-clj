;; Framework One (FW/1) Copyright (c) 2012-2015 Sean Corfield
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
  (:require [clojure.walk :as walk]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml]
            [clojure.stacktrace :as stacktrace]
            [ring.middleware.flash :as ring-f]
            [ring.middleware.params :as ring-p]
            [ring.middleware.resource :as ring-r]
            [ring.middleware.session :as ring-s]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.session.memory :refer [memory-store]]
            [selmer.parser]
            [selmer.filters]))

;; bridge in a couple of very useful Selmer symbols

(intern *ns* (with-meta 'add-tag!
               (meta #'selmer.parser/add-tag!))
        (deref #'selmer.parser/add-tag!))
(intern *ns* (with-meta 'add-filter!
               (meta #'selmer.filters/add-filter!))
        (deref #'selmer.filters/add-filter!))

;; scope access utility
(defn- scope-access [scope]
  (fn
    ([rc n] (get-in rc [::request scope n]))
    ([rc n v] (assoc-in rc [::request scope n] v))))

;; render data support
(defn- render-data [rc as expr]
  (assoc rc ::render {:as as :data expr}))

;; FW/1 base functionality

;; (start & config) - entry point to the framework

(def cookie (scope-access :cookies))

(def event (scope-access ::event))

(def flash (scope-access :flash))

(defn header
  "Either read the request headers or write the response headers."
  ([rc n] (get-in rc [::request :req-headers n]))
  ([rc n v] (assoc-in rc [::request :headers n] v)))

(defn remote-addr
  "Return the remote IP address for this request."
  [rc]
  (get-in rc [::request :remote-addr]))

(defn redirect [rc url]
  (assoc rc ::redirect {:status 302 :headers {"Location" url}}))

(defn reload? [rc]
  (let [config (event rc :config)
        reload (get rc (:reload config))
        password (:password config)]
    (or (and reload password (= reload password))
        (:reload-application-on-every-request config))))

(defn render-json [rc expr]
  (render-data rc :json expr))

(defn render-text [rc expr]
  (render-data rc :text expr))

(defn render-xml [rc expr]
  (render-data rc :xml expr))

(def session (scope-access :session))

(defn to-long [l]
  (try (Long/parseLong l) (catch Exception _ 0)))

;; FW/1 implementation
(defn- parts [req]
  (rest (.split req "/")))

(defn- compile-route [req]
  (let [verb? (.startsWith req "$")
        verb (if verb?
               (cond (.startsWith req "$GET") :get
                     (.startsWith req "$POST") :post
                     :else :any))]
    [verb
     (map (fn [part]
            (if (.startsWith part ":")
              (keyword (.substring part 1))
              part))
          (parts req))]))

(defn- match-part [p r]
  (cond
   (keyword? p) {p r}
   p            (= r p)
   :else        nil))

(defn- substitute-route [route lookup tail]
  (concat (map
           (fn [part]
             (if (keyword? part)
               (lookup part)
               part))
           route) tail))

(defn- matches-route [compiled-url method [verb compiled-route]]
  (if (or (empty? compiled-route)
          (and (not= :any verb)
               (not= verb method)))
    [::empty]
    (take-while identity
                (map match-part
                     (concat compiled-route (repeat nil))
                     (concat compiled-url (repeat nil))))))

(defn- pre-compile-routes [routes]
  (let [all-routes (apply concat routes)]
    [(map compile-route (map first all-routes))
     (map (comp second compile-route) (map second all-routes))]))

(defn- process-routes [routes new-routes url method]
  (let [[_ url] (compile-route url)
        matching (map (partial matches-route url method) routes)
        no-matches (count (take-while empty? matching))
        matches (first (drop no-matches matching))
        lookup (reduce (fn [a b]
                         (if (map? b) (merge a b) a)) {}
                         matches)
        url-rest (if (= [::empty] matches) url (drop (count matches) url))]
    (substitute-route (first (drop no-matches new-routes)) lookup url-rest)))

(defn- ->fs [path]
  (.replaceAll path "-" "_"))

(defn- as-map [route]
  (apply hash-map
         (if (even? (count route))
           route
           (concat route [""]))))

(defn- stem [config sep]
  (if-let [app (:application-key config)]
    (str app sep)
    ""))

(defn- get-view-nodes [config section item]
  (str (stem config "/") "views/" section "/" item "." (:suffix config)))

(defn- apply-controller [config controller-ns rc item]
  (if (or (::redirect rc)
          (::render rc))
    rc
    (if (keyword? item)
      (if-let [f (item config)] (f rc) rc)
      (if-let [f (resolve (symbol (str controller-ns "/" item)))] (f rc) rc))))

(defn- get-layout-nodes [config section item]
  (let [dot-html (str "." (:suffix config))]
    [(str (stem config "/") "layouts/" section "/" item dot-html)
     (str (stem config "/") "layouts/" section dot-html)
     (str (stem config "/") "layouts/default" dot-html)]))

(defn- apply-view [config rc section item]
  (when-let [view-nodes (get-view-nodes config section item)]
    (try
      (selmer.parser/render-file view-nodes rc (:selmer-tags config))
      (catch Exception _
        nil))))

(defn- apply-layout [config rc nodes layout-nodes]
  (try
    (selmer.parser/render-file layout-nodes
                               (assoc rc :body [:safe nodes])
                               (:selmer-tags config))
    (catch Exception _
      nodes)))

(defn- not-found []
  {:status 404
   :header {"Content-Type" "text/html; charset=utf-8"}
   :body "Not Found"})

(defn- render-page [config rc section item]
  (if-let [view-render (apply-view config rc section item)]
    (let [layout-cascade (get-layout-nodes config section item)
          final-html (reduce (partial apply-layout config rc) view-render layout-cascade)]
      {:status 200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body final-html})
    (not-found)))

(defn- as-xml
  [expr]
  (with-out-str (xml/emit (xml/sexp-as-element expr) *out*)))

(def ^:private render-types
  {:json {:type "application/json; charset=utf-8"
          :body json/write-str}
   :text {:type "text/plain; charset=utf-8"
          :body identity}
   :xml  {:type "text/xml; charset=utf-8"
          :body as-xml}})

(defn- render-data-response [{:keys [as data]}]
  (let [renderer (render-types as)]
    {:status 200
     :headers {"Content-Type" (:type renderer)}
     :body ((:body renderer) data)}))

(defn- require-controller [rc controller-ns]
  (try
    (if (reload? rc)
      (require controller-ns :reload)
      (require controller-ns))
    (catch Exception _
      nil)))

(defn- get-section-item [config route]
  (if (empty? route)
    (:home config)
    [(first route) (or (second route) (:default-item config))]))

(defn- pack-request
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

(defn- unpack-response
  "Given a request context and a response, return the response with Ring data added.
  By this point the response always has headers so we must add to those, not overwrite."
  [rc resp]
  (reduce (fn [m k]
            (if (= :headers k)
              (update m k merge (get-in rc [::request k]))
              (assoc m k (get-in rc [::request k]))))
          resp
          [:session :cookies :flash :headers]))

(defn- render-request [config req]
  (let [[routes new-routes] (:routes config)
        route (process-routes routes new-routes (:uri req) (:request-method req))
        [section item] (get-section-item config route)
        rc (-> (walk/keywordize-keys (merge (as-map (rest (rest route))) (:params req)))
               (pack-request req)
               (event :action  (str section "." item))
               (event :section section)
               (event :item    item)
               (event :config  config))
        controller-ns (symbol (str (stem config ".") "controllers." section))
        _ (require-controller rc controller-ns)
        rc (reduce (partial apply-controller config controller-ns)
                   rc
                   [:before "before" item "after" :after])]
    (->> (if-let [redirect (::redirect rc)]
           redirect
           (if-let [render-expr (::render rc)]
             (render-data-response render-expr)
             (render-page config rc section item)))
         (unpack-response rc))))

(defn- controller [config]
  (fn configured-controller [req]
    ;; since favicon.ico is commonly requested but often not present, we special case
    ;; it and return 404 Not Found rather than look for (and fail to find) that action!
    (if (= "/favicon.ico" (:uri req))
      (not-found)
      (try
        (render-request config req)
        (catch Exception e
          (if (::handling-exception req)
            (do
              (stacktrace/print-stack-trace e)
              {:status 500
               :body (str e)})
            (configured-controller (-> req
                                       (assoc ::handling-exception true)
                                       (assoc :uri (str "/" (first (:error config)) "/" (second (:error config))))
                                       (assoc-in [:params :exception] e)))))))))

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

(comment "Example of routes"
(let [[routes new-routes] (pre-compile-routes
                           [{"/list" "/user/list"}
                            {"/user/:id" "/user/view/id/:id"}
                            {"/" "/not/found"}])]
  (process-routes routes new-routes "/user/42/sort/email"))
)

(defn- merge-middleware [config]
  (fn [middleware]
    (if middleware
      (condp = (first middleware)
        :append (concat (default-middleware config) (rest middleware))
        :replace (rest middleware)
        :prepend (concat (rest middleware) (default-middleware config))
        (concat middleware (default-middleware config)))
      (default-middleware config))))

(defn- framework-defaults [options]
  (assoc options
         :error (if (:error options)
                  (clojure.string/split (:error options) #"\.")
                  [(:default-section options) "error"])
         :home  (if (:home options)
                  (clojure.string/split (:home options) #"\.")
                  [(:default-section options) (:default-item options)])
         :routes (pre-compile-routes (:routes options))))

(def ^:private default-options
  {:after identity
   :before identity
   :default-item "default"
   :default-section "main"
   :password "secret"
   :reload :reload
   :reload-application-on-every-request false
   :suffix "html" ; views / layouts would be .html
   :version "0.4.0"})

(defn start [& app-config]
  (let [options (merge default-options (apply hash-map app-config))
        dynamic-options (framework-defaults options)
        config (update-in dynamic-options [:middleware]
                          (merge-middleware dynamic-options))]
    (selmer.filters/add-filter! :empty? empty?)
    (reduce (fn [handler middleware] (middleware handler))
            (controller config)
            (:middleware config))))
