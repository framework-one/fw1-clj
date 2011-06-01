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
        res1 (resolve (symbol target))
        rc (if res1 (res1 params) params)
        res2 (resolve (symbol (str target "-view")))
        sub (if res2 (res2 rc) [])
        stuff (map #(%1 %2) (cycle [identity content]) sub)
        _ (println "sub" sub "stuff" stuff)
        temp (comp emit* 
                   (let [nodes (map annotate (html-resource (str "views/" section "/" item ".html")))]
                     (fn [rc]
                       (flatmap (fn [n] (at n (first stuff) (second stuff))) nodes))))] 
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (temp rc)}))

(defn app [req]
  (if (.endsWith (:uri req) "/favicon.ico")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "favicon.ico not found"}
    ((wrap-params app-base) req)))