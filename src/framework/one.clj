(ns framework.one
  (:require [clojure.walk :as walk])
  (:require [ring.middleware.params :as ring-p])
  (:require [ring.middleware.resource :as ring-r])
  (:require [net.cgrand.enlive-html :as html]))

(declare config)

;; Enlive bridge
(def ^:private enlive-symbols
  ['append 'at 'clone-for 'content 'do-> 'html-content 'prepend 'remove-class 'set-attr 'substitute])

(defmacro enlive-alias ^:private [sym]
  `(let [enlive-sym# (resolve (symbol (str "html/" ~sym)))]
     (intern *ns* (with-meta ~sym (meta enlive-sym#)) (deref enlive-sym#))))

(doseq [sym enlive-symbols]
  (enlive-alias sym))

;; Enlive extensions
(defn append-attr [attr v]
  #((set-attr attr (str (get-in % [:attrs attr] "") v)) %))

;; FW/1 base functionality

;; (start & config) - entry point to the framework

(defn redirect [rc url]
  (assoc rc ::redirect {:status 302 :headers {"Location" url}}))

(defn reload? [rc]
  (let [config @config
        reload (get rc (:reload config))
        password (:password config)]
    (and reload password (= reload password))))

(defn to-long [l]
  (try (Long/parseLong l) (catch Exception _ 0)))

;; FW/1 implementation
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

(defn- stem [sep]
  (let [config @config]
    (if-let [app (:application-key config)]
      (str app sep)
      "")))

(defn- get-view-nodes [section item]
  (get-cached-nodes [:view section item]
                    (str (stem "/") "views/" section "/" item ".html") :required true))

(defn- apply-controller [controller-ns rc item]
  (if (::redirect rc)
    rc
    (if-let [f (resolve (symbol (str controller-ns "/" item)))] (f rc) rc)))

(defn- get-layout-nodes [controller-ns section item]
  (let [config @config]
    [[(get-cached-nodes [:layout section item]
                        (str (stem "/") "layouts/" section "/" item ".html"))
      (resolve (symbol (str controller-ns "/" item "-layout")))]
     [(get-cached-nodes [:layout section]
                        (str (stem "/") "layouts/" section ".html"))
      (resolve (symbol (str controller-ns "/layout")))]
     [(get-cached-nodes [:layout]
                        (str (stem "/") "layouts/default.html"))
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
        controller-ns (symbol (str (stem ".") "controllers." section))
        _ (if (or (reload? rc)
                  (:reload-application-on-every-request config))
            (do
              (reset! node-cache {})
              (require controller-ns :reload-all))
            (require controller-ns))
        rc (reduce (partial apply-controller controller-ns) rc ["before" item "after"])]
    (if-let [redirect (::redirect rc)]
      redirect
      (let [view-nodes (get-view-nodes section item)
            view-process (resolve (symbol (str controller-ns "/" item "-view")))
            view-render (if view-process
                          (view-process rc view-nodes)
                          view-nodes)
            layouts (get-layout-nodes controller-ns section item)
            view-render (reduce (partial apply-layout rc) view-render layouts)
            view-html (apply str (html/emit* view-render))]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body view-html}))))

(defn- wrapper [req]
  ((-> controller
     ring-p/wrap-params
     (ring-r/wrap-resource (stem "/"))) req))

(defn- framework-defaults [options]
  (if (:error options)
    (assoc options :error (.split (:error options) "."))
    (assoc options :error [(:default-section options) "error"])))

(defn start [& app-config]
  (def ^:private config (atom {}))
  (let [defaults {:default-item "default"
                  :default-section "main"
                  :password "secret"
                  :reload :reload
                  :reload-application-on-every-request false}
        my-config (framework-defaults (merge defaults (apply hash-map app-config)))]
    (reset! config my-config)
    (var wrapper)))