(ns usermanager.controllers.user
  (:require [ring.util.response :as resp]
            [selmer.parser :as tmpl]
            [usermanager.model.user-manager :as model]))

(def ^:private changes
  "Count the number of changes (since the last reload)."
  (atom 0))

(defn before [req]
  ;; whatever needs doing at the start of the request
  req)

(defn after [req]
  (if (resp/response? req)
    req
    ;; no response so far, render an HTML template
    (let [data (assoc (:params req) :changes @changes)
          base (-> req :application/component :config :resources)
          view (:application/view req "default")
          html (tmpl/render-file (str base "/views/user/" view ".html") data)]
      (-> (resp/response (tmpl/render-file (str base "/layouts/default.html")
                                           (assoc data :body [:safe html])))
          (resp/content-type "text/html")))))

(defn reset-changes [req]
  (reset! changes 0)
  (assoc-in req [:params :message] "The change tracker has been reset."))

(defn default [req]
  (assoc-in req [:params :message]
                (str "Welcome to the User Manager application demo! "
                     "This uses just Compojure, Ring, and Selmer.")))

(defn delete-by-id [req]
  (swap! changes inc)
  (model/delete-user-by-id (get-in req [:params :id]))
  (resp/redirect "/user/list"))

(defn edit [req]
  (let [user (model/get-user-by-id (get-in req [:params :id]))]
    (-> req
        (update :params assoc
                :user user
                :departments (model/get-departments))
        (assoc :application/view "form"))))

(defn get-users [req]
  (let [users (model/get-users)
        add-department (fn [u]
                         (assoc u :department
                                (:name (model/get-department-by-id (:department-id u)))))]
    (-> req
        (assoc-in [:params :users] (map add-department users))
        (assoc :application/view "list"))))

(defn save [req]
  (swap! changes inc)
  (let [{:keys [id first-name last-name email department-id]} (:params req)]
    ;; note: need to convert form variables from a string to a number:
    (model/save-user {:id (when (seq id) (Long/parseLong id))
                      :first-name first-name :last-name last-name
                      :email email :department-id (Long/parseLong department-id)}))
  (resp/redirect "/user/list"))
