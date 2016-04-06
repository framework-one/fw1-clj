;; Copyright (c) 2016 Sean Corfield

(ns basic.expectations.server
  (:require [framework.one :as fw1]
            [expectations :refer [expect more-of]]
            [clojure.string :as str]))

(expect (more-of {:keys [status body]}
                 200         status
                 "Default\n" body)
        ((fw1/start) {:uri "/"}))

(expect (more-of {:keys [status body]}
                 200           status
                 "Test Item\n" body)
        ((fw1/start :home "test.item") {:uri "/"}))

(expect (more-of {:keys [status body]}
                 200           status
                 "Test Item\n" body)
        ((fw1/start {:default-section "test" :default-item "item"}) {:uri "/"}))

(expect (more-of {:keys [status body]}
                 404         status
                 "Not Found" body)
        ((fw1/start {:routes [{"$GET/foo" "/"}
                              {"/:section/:item" "/"}]}) {:uri "/bar" :request-method :post}))

(expect (more-of {:keys [status body]}
                 404         status
                 "Not Found" body)
        ((fw1/start {:routes [{"$GET/foo" "/"}
                              {"*" "404:/"}]}) {:uri "/bar" :request-method :post}))

(expect (more-of {:keys [status headers body]}
                 302    status
                 "/foo" (some (fn [[h v]] (when (= "Location" h) v)) headers)
                 nil    body)
        ((fw1/start {:routes [{"$GET/foo" "/"}
                              {"/bar" "302:/foo"}
                              {"*" "404:/"}]}) {:uri "/bar" :request-method :post}))

(defn- header-name?
  [name]
  (fn [header]
    (str/starts-with? (ffirst header) name)))

(expect (more-of {:keys [status headers body]}
                 200 status
                 ""  body
                 6   (count headers)
                 5   (count (filter (header-name? "Access-Control") headers))
                 4   (count (filter (header-name? "Access-Control-Allow") headers)))
        ((fw1/start) {:uri "/" :request-method :options}))
