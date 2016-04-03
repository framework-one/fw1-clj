;; Copyright (c) 2016 Sean Corfield

(ns controllers.renderer
  "Test controller for verifying render data functions."
  (:require [framework.one :as fw1]))

(defn do-html [rc]
  (if (:status rc)
    (fw1/render-html rc 204 "<h1>Hello!</h1>")
    (fw1/render-html rc "<h1>Hello!</h1>")))

(defn do-json [rc]
  (if (:status rc)
    (fw1/render-json rc 500 {:h1 "Bang!"})
    (fw1/render-json rc {:h1 "Hello!"})))

(defn do-text [rc]
  (if (:status rc)
    (fw1/render-text rc (fw1/to-long (:status rc)) "With Status")
    (fw1/render-text rc "Hello!")))

(defn do-xml [rc]
  (if (:status rc)
    (fw1/render-xml rc 201 [:h1 "Hello!"])
    (fw1/render-xml rc [:h1 "Hello!"])))
