;; copyright (c) 2016 sean corfield

(ns basic.expectations.server
  (:require [framework.one :as fw1]
            [expectations :refer [expect more-of]]))

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
                 500         status
                 #"Exception.*views/bar/default.html.*doesn't exist" body)
        ((fw1/start {:routes [{"$GET/foo" "/"}
                              {"/:section/:item" "/"}]}) {:uri "/bar" :request-method :post}))
