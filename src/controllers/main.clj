(ns controllers.main
  (:use net.cgrand.enlive-html))

(defn index [rc]
  (assoc rc :rationale ["what" "when" "how"]))

(defn default [rc]
  (assoc rc :it "main.default controller called"))

(defn default-view [nodes rc]
  (at nodes
      [:p#message] (content (:it rc))))

(defn index-view [nodes rc]
  (at nodes
      [:li.items]
      (clone-for [item (:rationale rc)]
                 (content item))))