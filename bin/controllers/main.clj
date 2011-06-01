(ns controllers.main)

(defn default [rc]
  (assoc rc :it "main.default controller called"))

(defn default-view [rc]
  (list [:p#message] (:it rc)))