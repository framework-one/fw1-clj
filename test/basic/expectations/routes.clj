;; copyright (c) 2016 sean corfield

(ns basic.expectations.routes
  (:require [framework.one :as fw1]
            [expectations :refer [expect more-of]]))

(expect ["list"] (fw1/matches-route (list "list" "foo" "bar") :get [:get ["list"]]))

(expect :framework.one/not-found (fw1/matches-route (list "list" "foo" "bar") :post [:get ["list"]]))

(expect :framework.one/not-found (fw1/matches-route (list "xlist" "foo" "bar") :get [:get ["list"]]))

(expect [{:section "xlist"}] (fw1/matches-route (list "xlist") :get [:get [:section]]))

(expect :framework.one/not-found (fw1/matches-route (list "xlist") :get [:get [:section :item]]))

(expect [[[:any ["list"]]] [["product" "list"]]] (fw1/pre-compile-routes [{"/list" "/product/list"}]))

(expect [[[:get ["list"]]] [["product" "list"]]] (fw1/pre-compile-routes [{"$GET/list" "/product/list"}]))

(expect [[[:post ["list"]]] [["product" "list"]]] (fw1/pre-compile-routes [{"$POST/list" "/product/list"}]))

(expect [[[:put ["list"]]] [["product" "list"]]] (fw1/pre-compile-routes [{"$PUT/list" "/product/list"}]))

(expect [[[:patch ["list"]]] [["product" "list"]]] (fw1/pre-compile-routes [{"$PATCH/list" "/product/list"}]))

(expect [[[:delete ["list"]]] [["product" "list"]]] (fw1/pre-compile-routes [{"$DELETE/list" "/product/list"}]))
