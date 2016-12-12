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

(ns framework.one.spec
  "Legacy specification for original FW/1 request context etc."
  (:require [clojure.spec :as s]))

(alias 'fw1 (create-ns 'framework.one))

;; FW/1 request context always has event data and a Ring request
;; and may have any arbitrary unqualified keys which represent the
;; form and URL parameters for a request (and any data added by a
;; controller, available for the views).
;; In addition, a controller may choose to redirect or render data.

(s/def ::fw1/rc (s/keys :req [::fw1/event ::fw1/ring]
                        :opt [::fw1/redirect ::fw1/render]))

(s/def ::fw1/event (s/keys :req-un [::action ::section ::item ::config]
                           :opt-un [::headers]))

(s/def ::action string?)
(s/def ::section string?)
(s/def ::item string?)
(s/def ::config (s/map-of keyword? any?))

(s/def ::fw1/redirect (s/keys :req-un [::status :location/headers]))
(s/def :location/headers (s/map-of #{"Location"} string?))

(s/def ::fw1/render (s/keys :req-un [::as ::data] :opt-un [::status]))
(s/def ::as #{:html :json :raw-json :text :xml})
(s/def ::data any?)

;; basic Ring request

(s/def ::fw1/ring (s/keys :req-un [::headers ::protocol ::remote-addr
                                   ::request-method ::scheme
                                   ::server-name ::server-port
                                   ::uri]
                          :opt-un [::body
                                   ::character-encoding
                                   ::content-length ::content-type
                                   ::query-string ::ssl-client-cert]))

(s/def ::headers (s/map-of string? string?))
(s/def ::protocol string?)
(s/def ::remote-addr string?)
(s/def ::request-method #{:delete :get :head :options :patch :post :put})
(s/def ::scheme #{:http :https})
(s/def ::server-name string?)
(s/def ::server-port pos-int?)
(s/def ::uri string?)

(s/def ::body string?) ; mostly!
(s/def ::character-encoding string?) ; deprecated
(s/def ::content-length nat-int?) ; deprecated
(s/def ::content-type string?) ; deprecated
(s/def ::query-string string?)
(s/def ::ssl-client-cert any?)

;; Ring response map (which is also FW/1's response map)

(s/def ::fw1/response (s/keys :req-un [::headers ::status]
                              :opt-un [::body]))

(s/def ::status pos-int?)
