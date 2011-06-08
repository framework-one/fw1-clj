(ns fw1-clj.core
  (:use [ring.adapter.jetty])
  (:use [ring.middleware.reload])
  (:use [ring.middleware.params])
  (:use [net.cgrand.enlive-html]))

(defn app-base [req]
  (let [route (clojure.string/split (:uri req) #"/")
        section (or (first (rest route)) "main")
        item (or (second (rest route)) "default")
        target (str "controllers." section "/" item)
        params (:params req)
        controller (resolve (symbol target))
        rc (if controller (controller params) params)
        view-process (resolve (symbol (str target "-view")))
        rule-base (if view-process (view-process rc) [])
        substitions (map #(%1 %2) (cycle [identity content]) rule-base)
        temp (comp emit* 
                   (let [nodes (map annotate (html-resource (str "views/" section "/" item ".html")))]
                     (fn [rc]
                       (flatmap (fn subst [n] (loop [rules (partition 2 rule-base) node-list (as-nodes n)] 
                                          (if (nil? rules) node-list 
                                            (let [[[s t] & more] rules] 
                                              (if (vector? t)
                                                (do
                                                  (println "transform-vector" t)
                                                  (recur more (mapcat #(transform node-list s (content %)) t)))
                                                (do
                                                  (println "transform" t)
                                                  (recur more (transform node-list s (content t))))))))) 
                                nodes))))] 
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (temp rc)}))

(defn app [req]
  (if (.endsWith (:uri req) "/favicon.ico")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "favicon.ico not found"}
    ((wrap-params app-base) req)))