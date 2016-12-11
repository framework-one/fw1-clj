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
            [ring.util.response :as resp]))

(defn wrap-before-after
  "Given a handler, return a new handler that executes the specified :before
  and :after handlers around the original handler, stopping if any of them
  return an Ring response.
  In addition, for ease of migration, optionally turn the Ring request into a
  'request context' by blending :params and :flash into the main request."
  [handler & {:keys [before after legacy? config]}]
  (fn [req]
    (let [req  (cond-> (assoc req :framework.one/config config)
                 legacy? (merge (:params req) (:flash req)
                                {:framework.one/unrolled true})
                 before  before)
          resp (cond-> req
                 (not (resp/response? req)) handler)]
      (cond-> resp
        (and (not (resp/response? resp)) after) after))))

(defn wrap-view-layout
  "Given a handler, return a new handler that runs the original handler and, if
  that doesn't return a Ring response, locates and renders a view and a cascade
  of layouts, if :framework.one/view is specified in the response."
  [handler & {:keys [config]}]
  (fn [req]
    (let [resp (handler (assoc req :framework.one/config config))]
      (if (resp/response? resp)
        resp
        (view/render-page resp)))))
