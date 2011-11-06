(ns framework.one
  (:require [clojure.walk :as walk])
  (:require [ring.middleware.params :as ring-p])
  (:require [net.cgrand.enlive-html :as html]))

;; Enlive bridge
(def ^:private enlive-symbols ['append 'at 'clone-for 'content 'do-> 'html-content 'remove-class 'set-attr 'substitute])

(defmacro enlive-alias ^:private [sym]
  `(let [enlive-sym# (resolve (symbol (str "html/" ~sym)))]
     (intern *ns* (with-meta ~sym (meta enlive-sym#)) (deref enlive-sym#))))

(doseq [sym enlive-symbols]
  (enlive-alias sym))

;; FW/1 base functionality

(declare config)
(def ^:private node-cache (atom {}))

(defn- parts [req] (rest (.split (:uri req) "/")))

(defn- get-cached-nodes [node-key node-path & {:keys [required] :or {required false}}]
  (or (get @node-cache node-key)
      (let [nodes (try
                    (html/html-resource node-path)
                    (catch Exception e
                      (if required (throw e) nil)))]
        (swap! node-cache #(assoc % node-key nodes))
        nodes)))

(defn- as-map [route]
  (apply hash-map
         (if (even? (count route))
           route
           (concat route [""]))))

(defn- get-view-nodes [section item]
  (get-cached-nodes [:view section item] (str "views/" section "/" item ".html") :required true))

(defn- apply-controller [controller-ns rc item]
  (if-let [f (resolve (symbol (str controller-ns "/" item)))] (f rc) rc))

(defn- get-layout-nodes [controller-ns section item]
  (let [config @config]
    [[(get-cached-nodes [:layout section item] (str "layouts/" section "/" item ".html"))
      (resolve (symbol (str controller-ns "/" item "-layout")))]
     [(get-cached-nodes [:layout section] (str "layouts/" section ".html"))
      (resolve (symbol (str controller-ns "/layout")))]
     [(get-cached-nodes [:layout] "layouts/default.html")
      (:layout config)]]))

(defn- apply-layout [rc nodes [layout-nodes layout-process]]
  (if layout-nodes
    (let [layout-nodes (at layout-nodes [:#body] (substitute nodes))]
      (if layout-process
        (layout-process rc layout-nodes)
        layout-nodes))
    nodes))

(defn- controller [req]
  (let [config @config
        route (parts req)
        rc (walk/keywordize-keys (merge (as-map (rest (rest route))) (:params req)))
        section (or (first route) (:default-section config))
        item (or (second route) (:default-item config))
        controller-ns (symbol (str "controllers." section))
        _ (if (:reload-application-on-every-request config)
            (do
              (reset! node-cache {})
              (require controller-ns :reload))
            (require controller-ns))
        rc (reduce (partial apply-controller controller-ns) rc ["before" item "after"])
        view-nodes (get-view-nodes section item)
        view-process (resolve (symbol (str controller-ns "/" item "-view")))
        view-render (if view-process
                      (view-process rc view-nodes)
                      view-nodes)
        layouts (get-layout-nodes controller-ns section item)
        view-render (reduce (partial apply-layout rc) view-render layouts)
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

(defn- framework-defaults [options]
  (if (:error options)
    (assoc options :error (.split (:error options) "."))
    (assoc options :error [(:default-section options) "error"])))

(defn start [& app-config]
  (def ^:private config (atom {}))
  (let [defaults {:default-section "main"
                  :default-item "default"
                  :reload-application-on-every-request false}
        my-config (framework-defaults (merge defaults (apply hash-map app-config)))]
    (reset! config my-config)
    (var wrapper)))