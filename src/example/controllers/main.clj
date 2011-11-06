(ns example.controllers.main
  (:use framework.one))

;; controller methods
(defn index [rc]
  (assoc rc :rationale ["what" "when" "how"]))

(defn default [rc]
  (assoc rc :it "main.default controller called"))

;; view methods
(defn default-view [rc nodes]
  (at nodes
      [:p#message] (content (:it rc))))

(defn index-view [rc nodes]
  (at nodes
      [:li.items]
      (clone-for [item (:rationale rc)]
                 (content item))
      [:#rc]
      (content (pr-str rc))))