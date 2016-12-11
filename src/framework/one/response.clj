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

(ns framework.one.response
  (:require [cheshire.core :as json]
            [clojure.data.xml :as xml]
            [ring.util.response :as resp]))

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
  (-> (let [full-render-types (merge render-types (:render-types config))]
        (if (fn? as)
          (let [content-type (as)]
            (-> (resp/response (as config data))
                (resp/content-type (cond (string? content-type)
                                         content-type
                                         (keyword? content-type)
                                         (:type (full-render-types content-type))
                                         :else
                                         (throw (ex-info "Unsupported content-type type"
                                                         {:type content-type}))))))
          (let [renderer (full-render-types as)]
            (-> (resp/response ((:body renderer) config data))
                (resp/content-type (:type renderer))))))
      (resp/status status)))

(defn render-data
  "Render this expression as the given type.
  Prefer the convenience functions below, unless you are rendering
  custom data types."
  ([req as expr]
   (render-data-response (:framework.one/config req) {:as as :data expr}))
  ([req status as expr]
   (render-data-response (:framework.one/config req) {:status status :as as :data expr})))

(defn render-by
  "Given a rendering function, return a convenience function that
  accepts the request, the optional status code to return, and the expression
  to render."
  [render-fn]
  (fn
    ([req expr]        (render-data req render-fn expr))
    ([req status expr] (render-data req status render-fn expr))))

(defn render-html
  "Render this expression (string) as-is as HTML."
  ([req expr]
   (render-data req :html expr))
  ([req status expr]
   (render-data req status :html expr)))

(defn render-json
  "Render this expression as JSON."
  ([req expr]
   (render-data req :json expr))
  ([req status expr]
   (render-data req status :json expr)))

(defn render-raw-json
  "Render this string as raw (encoded) JSON."
  ([req expr]
   (render-data req :raw-json expr))
  ([req status expr]
   (render-data req status :raw-json expr)))

(defn render-text
  "Render this expression (string) as plain text."
  ([req expr]
   (render-data req :text expr))
  ([req status expr]
   (render-data req status :text expr)))

(defn render-xml
  "Render this expression as XML.
  Uses clojure.data.xml's sexp-as-element - see those docs for more detail."
  ([req expr]
   (render-data req :xml expr))
  ([req status expr]
   (render-data req status :xml expr)))
