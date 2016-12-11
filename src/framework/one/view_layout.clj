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

(ns framework.one.view-layout
  (:require [framework.one.request :as req]
            [ring.util.response :as resp]
            [selmer.parser]
            [selmer.util :refer [resource-path]]))

;; some utilities lifted from the original FW/1 code:

(defn ->fs
  "Given a Clojure ns/symbol, return a filesystem path. This just follows the
  convention that a - in a namespace/function becomes a _ in a filename."
  [path]
  (.replaceAll path "-" "_"))

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

(defn get-view-path
  "Given the application configuration, and the section and item, return the path to
  the matching view (which may or may not exist)."
  [config section item]
  (->fs (str (stem config "/") "views/" section "/" item "." (:suffix config "html"))))

(defn get-layout-paths
  "Given the application configuration, and the section and item, return the sequence of
  applicable (and existing!) layouts."
  [config section item]
  (let [dot-html (str "." (:suffix config "html"))]
    (filter resource-path
            (map ->fs [(str (stem config "/") "layouts/" section "/" item dot-html)
                       (str (stem config "/") "layouts/" section dot-html)
                       (str (stem config "/") "layouts/default" dot-html)]))))

(defn apply-view
  "Given the application configuration, the request, the sction and item, and a
  flag that indicates whether we're already processing an exception, attempt to
  render the matching view."
  [config req section item exceptional?]
  (let [view-path   (get-view-path config section item)
        render-view (fn [] (selmer.parser/render-file view-path req (:selmer-tags config)))]
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
  [config req exceptional? html layout-path]
  (let [render-layout (fn [] (selmer.parser/render-file layout-path
                                                        (assoc req :body [:safe html])
                                                        (:selmer-tags config)))]
    (if exceptional?
      ;; if we fail to render a layout while processing an exception
      ;; just treat the layout as not found so the original view will
      ;; be returned unadorned
      (try (render-layout) (catch Exception _ html))
      (render-layout))))

(defn as-html
  "Given a response, set the content type to HTML and the charset to UTF-8."
  [resp]
  (-> resp
      (resp/content-type "text/html")
      (resp/charset "utf-8")))

(defn html-response
  "Convenience method to return an HTML response (with a status and body)."
  [status body]
  (-> (resp/response body)
      (resp/status status)
      (as-html)))

(defn not-found
  "Return a basic 404 Not Found page."
  []
  (-> (resp/not-found "Not Found")
      (as-html)))

;; public API:

(defn render-page
  "Given a Ring request, if it contains :framework.one/view, then locate and
  render a view and a cascade of layouts based on that :section/item."
  ([req]
   (if-let [action (:framework.one/view req)]
     (render-page (req/config req) req (namespace action) (name action) false)
     req))
  ([config req section item exceptional?]
   ;; this is the legacy FW/1 render-page logic:
   (if-let [view-render (apply-view config req section item exceptional?)]
     (let [layout-cascade (get-layout-paths config section item)
           final-html (reduce (partial apply-layout config req exceptional?) view-render layout-cascade)]
       (html-response 200 final-html))
     (if exceptional?
       (html-response 500 (if-let [e (:exception req)] (str e) "Unknown Error"))
       (not-found)))))
