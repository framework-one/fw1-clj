;; copyright (c) 2016 sean corfield

(ns basic.expectations.server
  (:require [framework.one :as fw1]
            [expectations :refer [expect more-of]]))

(expect (more-of {:keys [status body]}
                 200         status
                 "Default\n" body)
        ((fw1/start) {:uri "/"}))
