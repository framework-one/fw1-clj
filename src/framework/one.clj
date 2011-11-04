(ns framework.one
  (:use [ring.adapter.jetty])
  (:use [ring.middleware.reload])
  (:use [ring.middleware.params])
  (:use [net.cgrand.enlive-html]))

(defn app-base [req]
  (let [route (clojure.string/split (:uri req) #"/")
        section (or (first (rest route)) "main")
        item (or (second (rest route)) "default")
        controller-ns (str "controllers." section)
        _ (require (symbol controller-ns))
        target (str controller-ns "/" item)
        params (:params req)
        controller (resolve (symbol target))
        rc (if controller (controller params) params)
        view-process (resolve (symbol (str target "-view")))
        view-nodes (html-resource (str "views/" section "/" item ".html"))
        view-render (when view-process
                      (view-process view-nodes rc))
        view-html (apply str (emit* view-render))]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body view-html}))

(defn app [req]
  (if (.endsWith (:uri req) "/favicon.ico")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "favicon.ico not found"}
    ((wrap-params app-base) req)))