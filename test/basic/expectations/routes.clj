;; copyright (c) 2016 sean corfield

(ns basic.expectations.routes
  (:require [framework.one :as fw1]
            [expectations :refer [expect more-of]]))

(expect ["list"] (fw1/matches-route (list "list" "foo" "bar") :get [:get ["list"]]))

(expect :framework.one/not-found (fw1/matches-route (list "list" "foo" "bar") :post [:get ["list"]]))

(expect :framework.one/not-found (fw1/matches-route (list "xlist" "foo" "bar") :get [:get ["list"]]))

(expect [{:section "xlist"}] (fw1/matches-route (list "xlist") :get [:get [:section]]))

(expect :framework.one/not-found (fw1/matches-route (list "xlist") :get [:get [:section :item]]))
