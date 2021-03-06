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

(ns framework.one.request
  (:require [clojure.string :as str]))

(defn config
  "Given a request, return the configuration map."
  [req]
  (:framework.one/config req {}))

(defn legacy?
  "Given a request, return true if it is a legacy FW/1 request context
  (rather than a pure Ring request).
  Like the :legacy? option on the middeware, this is intended purely to aid
  with conversion of older FW/1 applications."
  [req]
  (and (:framework.one/event req)
       (:framework.one/ring req)))

(defn remote-addr
  "Return the remote IP address for this request."
  [req]
  (get-in req [:remote-addr]))

(defn servlet-request
  "Return a fake HttpServletRequest that knows how to delegate to the request."
  [req]
  (let [ring   (if (legacy? req) (:framework.one/ring req) req)
        params (if (:framework.one/unrolled req) req (:params req))]
    (proxy [javax.servlet.http.HttpServletRequest] []
      (getContentType []
        (get-in ring [:headers "content-type"]))
      (getHeader [name]
        (get-in ring [:headers (str/lower-case name)]))
      (getMethod []
        (-> ring :request-method name str/upper-case))
      (getParameter [name]
        (when-let [v (get params (keyword name))] (str v))))))
