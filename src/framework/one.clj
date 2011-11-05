(ns framework.one
  (:require [ring.middleware.params :as ring-p])
  (:require [net.cgrand.enlive-html :as html]))

;; Enlive bridge
(def ^:private enlive-symbols ['at 'clone-for 'content])

(defmacro enlive-alias ^:private [sym]
  `(let [enlive-sym# (resolve (symbol (str "html/" ~sym)))]
     (intern *ns* (with-meta ~sym (meta enlive-sym#)) (deref enlive-sym#))))

(doseq [sym enlive-symbols]
  (enlive-alias sym))

;; FW/1 base functionality

(declare config)
(declare view-cache)

(defn- parts [req] (rest (clojure.string/split (:uri req) #"/")))

(defn- view [section item]
  (or (get @view-cache [section item])
      (let [view-nodes (html/html-resource (str "views/" section "/" item ".html"))]
        (swap! view-cache #(assoc % [section item] view-nodes))
        view-nodes)))

(defn- controller [req]
  (let [config @config
        route (parts req)
        rc (:params req)
        section (or (first route) (:default-section config))
        item (or (second route) (:default-item config))
        controller-ns (symbol (str "controllers." section))
        _ (if (:reload-application-on-every-request config)
            (require controller-ns :reload)
            (require controller-ns))
        target (str controller-ns "/" item)
        controller (resolve (symbol target))
        rc (if controller (controller rc) rc)
        view-process (resolve (symbol (str target "-view")))
        view-nodes (view section item)
        view-render (if view-process
                      (view-process view-nodes rc)
                      view-nodes)
        view-html (apply str (html/emit* view-render))]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body view-html}))

(defn- wrapper [req]
  (if (.endsWith (:uri req) "/favicon.ico")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "favicon.ico not found"}
    ((ring-p/wrap-params controller) req)))

(defn start [& app-config]
  (def ^:private config (atom {}))
  (def ^:private view-cache (atom {}))
  (let [defaults {:default-section "main"
                  :default-item "default"
                  :reload-application-on-every-request false}
        my-config (merge defaults (apply hash-map app-config))]
    (reset! config my-config)
    (var wrapper)))