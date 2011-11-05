(ns controllers.main
  (:use framework.one))

;; controller methods
(defn index [rc]
  (assoc rc :rationale ["what" "when" "how"]))

(defn default [rc]
  (assoc rc :it "main.default controller called"))

;; view methods
(defn default-view [nodes rc]
  (at nodes
      [:p#message] (content (:it rc))))

(defn index-view [nodes rc]
  (at nodes
      [:li.items]
      (clone-for [item (:rationale rc)]
                 (content item))
      [:#rc]
      (content (pr-str rc))))