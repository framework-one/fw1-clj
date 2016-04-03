;; Copyright (c) 2016 Sean Corfield

(ns controllers.renderer
  "Test controller for verifying render data functions."
  (:require [framework.one :as fw1]))

(defn do-html [rc]
  (fw1/render-html rc "<h1>Hello!</h1>"))

(defn do-json [rc]
  (fw1/render-json rc {:h1 "Hello!"}))

(defn do-text [rc]
  (fw1/render-text rc "Hello!"))

(defn do-xml [rc]
  (fw1/render-xml rc [:h1 "Hello!"]))
