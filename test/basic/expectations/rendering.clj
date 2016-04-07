;; Copyright (c) 2016 Sean Corfield

(ns basic.expectations.rendering
  (:require [framework.one :as fw1]
            [expectations :refer [expect more-of]]
            [clojure.string :as str]))

(expect (more-of {:keys [status headers body]}
                 200                        status
                 "text/html; charset=utf-8" (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "<h1>Hello!</h1>"          body)
        ((fw1/start {}) {:uri "/renderer/do_html"}))

(expect (more-of {:keys [status headers body]}
                 204                        status
                 "text/html; charset=utf-8" (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "<h1>Hello!</h1>"          body)
        ((fw1/start {}) {:uri "/renderer/do_html/status/204"}))

(expect (more-of {:keys [status headers body]}
                 200                   status
                 "application/json; charset=utf-8"
                 (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "{\"h1\":\"Hello!\"}" body)
        ((fw1/start {}) {:uri "/renderer/do_json"}))

(expect (more-of {:keys [status headers body]}
                 200                   status
                 "application/json; charset=utf-8"
                 (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "{\"H1\":\"Hello!\"}" body)
        ((fw1/start {:json-config {:key-fn (comp str/upper-case name)}}) {:uri "/renderer/do_json"}))

(expect (more-of {:keys [status headers body]}
                 500                   status
                 "application/json; charset=utf-8"
                 (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "{\"h1\":\"Bang!\"}" body)
        ((fw1/start {}) {:uri "/renderer/do_json/status/500"}))

(expect (more-of {:keys [status headers body]}
                 200                         status
                 "text/plain; charset=utf-8" (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "Hello!"                    body)
        ((fw1/start {}) {:uri "/renderer/do_text"}))

(expect (more-of {:keys [status headers body]}
                 403                         status
                 "text/plain; charset=utf-8" (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "With Status"               body)
        ((fw1/start {}) {:uri "/renderer/do_text/status/403"}))

(expect (more-of {:keys [status headers body]}
                 200                       status
                 "text/xml; charset=utf-8" (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "<?xml version=\"1.0\" encoding=\"UTF-8\"?><h1>Hello!</h1>"
                 body)
        ((fw1/start {}) {:uri "/renderer/do_xml"}))

(expect (more-of {:keys [status headers body]}
                 201                       status
                 "text/xml; charset=utf-8" (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "<?xml version=\"1.0\" encoding=\"UTF-8\"?><h1>Hello!</h1>"
                 body)
        ((fw1/start {}) {:uri "/renderer/do_xml/status/201"}))
