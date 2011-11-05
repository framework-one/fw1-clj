(ns framework.one
  (:require [ring.middleware.params :as ring-p])
  (:require [net.cgrand.enlive-html :as html]))

;; Enlive bridge
(intern *ns* (with-meta 'at (meta #'html/at)) @#'html/at)
(intern *ns* (with-meta 'clone-for (meta #'html/clone-for)) @#'html/clone-for)
(intern *ns* (with-meta 'content (meta #'html/content)) @#'html/content)

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
        view-nodes (html/html-resource (str "views/" section "/" item ".html"))
        view-render (if view-process
                      (view-process view-nodes rc)
                      view-nodes)
        view-html (apply str (html/emit* view-render))]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body view-html}))

(defn app [req]
  (if (.endsWith (:uri req) "/favicon.ico")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "favicon.ico not found"}
    ((ring-p/wrap-params app-base) req)))