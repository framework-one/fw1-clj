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

(ns framework.one.middleware
  (:require [framework.one.view-layout :as view]
            [ring.middleware.cors :as ring-cors]
            [ring.middleware.defaults :as ring-md]
            [ring.middleware.json :as ring-json]
            [ring.util.response :as resp]))

(defn wrap-before-after
  "Given a handler and configuration, return a new handler that executes the
  specified :before and :after handlers around the original handler. If :before
  returns a Ring response, do not call the handler, but always call :after.
  In addition, for ease of migration, optionally turn the Ring request into a
  'request context' by blending :params and :flash into the main request. This
  is not the default because this is for conversion of older programs only!"
  [handler config]
  (let [{:keys [before after legacy?]} config]
    (fn [req]
      (if (resp/response? req)
        req
        (let [req  (cond-> req
                     (not (:framework.one/config req))
                     (assoc :framework.one/config config)
                     legacy?
                     (merge (:params req) (:flash req) {:framework.one/unrolled true})
                     before
                     before)
              resp (cond-> req
                     (not (resp/response? req)) handler)]
          (cond-> resp
            after after))))))

(defn wrap-view-layout
  "Given a handler and configuration, return a new handler that runs the
  original handler and, if that doesn't return a Ring response, locates and
  renders a view and a cascade of layouts, if :framework.one/view is specified
  in the response."
  [handler config]
  (fn [req]
    (if (resp/response? req)
      req
      (let [resp (handler (assoc req :framework.one/config config))]
        (if (resp/response? resp)
          resp
          (view/render-page resp))))))

(defn wrapper
  "Given the application configuration, produce configured middleware."
  [config]
  (let [{:keys [middleware-default-fn middleware-wrapper-fn]
         :or   {middleware-default-fn identity
                middleware-wrapper-fn identity}} config]
    (fn [handler]
      (-> handler
          (wrap-before-after config)
          (ring-md/wrap-defaults (-> ring-md/site-defaults
                                     (assoc-in [:security :anti-forgery] false)
                                     (assoc-in [:proxy] true)
                                     (middleware-default-fn)))
          (ring-json/wrap-json-params)
          (ring-json/wrap-json-response (:json-config config))
          (middleware-wrapper-fn)
          ;; CORS should go outside routes!
          (wrap-view-layout config)))))
