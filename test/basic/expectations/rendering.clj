;; Copyright (c) 2017 Sean Corfield

(ns basic.expectations.rendering
  (:require [framework.one :as fw1]
            [expectations :refer [expect more-of]]))

(expect (more-of {:keys [status headers body]}
                 200                        status
                 "text/html; charset=utf-8" (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "<h1>Hello!</h1>"          body)
        ((fw1/start {}) {:uri "/renderer/do_html"}))

(expect (more-of {:keys [status headers body]}
                 200                   status
                 "application/json; charset=utf-8"
                 (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "{\"h1\":\"Hello!\"}" body)
        ((fw1/start {}) {:uri "/renderer/do_json"}))

(expect (more-of {:keys [status headers body]}
                 200                         status
                 "text/plain; charset=utf-8" (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "Hello!"                    body)
        ((fw1/start {}) {:uri "/renderer/do_text"}))

(expect (more-of {:keys [status headers body]}
                 200                       status
                 "text/xml; charset=utf-8" (some (fn [[h v]] (when (= "Content-Type" h) v)) headers)
                 "<?xml version=\"1.0\" encoding=\"UTF-8\"?><h1>Hello!</h1>"
                 body)
        ((fw1/start {}) {:uri "/renderer/do_xml"}))
