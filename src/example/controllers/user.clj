(ns example.controllers.user
  (:use framework.one))

;; controller methods
(defn default [rc]
  (assoc rc :message "main.default controller called"))

(defn form [rc])

(defn list [rc])

;; view methods
(defn default-view [rc nodes]
  (at nodes
      [:p#message] (content (:message rc))
      [:p#reload] (if (:reload rc)
                    (content "The framework cache (and application scope) have been reset.")
                    (substitute ""))))

(defn form-view [rc nodes])

(defn form-list [rc nodes])