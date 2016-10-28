;; Copyright (c) 2016 Sean Corfield

(ns basic.expectations.server
  (:require [framework.one :as fw1]
            [expectations :refer [expect more-of]]
            [clojure.string :as str]))

(expect "123.45.67.0"
        (fw1/remote-addr {:framework.one/ring {:headers {"x-forwarded-for" "123.45.67.0"}
                                               :remote-addr "1.23.45.67"}}))

(expect "1.23.45.67"
        (fw1/remote-addr {:framework.one/ring {:headers {}
                                               :remote-addr "1.23.45.67"}}))

(expect (more-of {:keys [status body]}
                 200         status
                 "Default\n" body)
        (((fw1/configure-router {})) {:uri "/"}))

(expect (more-of {:keys [status body]}
                 200           status
                 "Test Item\n" body)
        (((fw1/configure-router {:home "test.item"})) {:uri "/"}))

(expect (more-of {:keys [status body]}
                 200           status
                 "Test Item\n" body)
        (((fw1/configure-router {:default-section "test" :default-item "item"})) {:uri "/"}))

(defn- header-name?
  [name]
  (fn [header]
    (str/starts-with? (first header) name)))

(expect (more-of {:keys [status headers body]}
                 200            status
                 ""             body
                 (partial <= 6) (count headers)
                 5              (count (filter (header-name? "Access-Control") headers))
                 4              (count (filter (header-name? "Access-Control-Allow") headers)))
        (((fw1/configure-router {})) {:uri "/" :request-method :options}))
