(ns usermanager.controllers.user
  (:require [framework.one :refer :all]
            [usermanager.model.user-manager :refer :all]))

;; note that -view and -layout functions are only used for the Enlive
;; version of FW/1 - the Selmer version of FW/1 automatically renders
;; templates based on what is in rc

;; controller methods

(defn default [rc]
  (assoc rc
    :message        "Welcome to the Framework One User Manager application demo!"
    :reload-message (when (reload? rc)
                      "The framework cache (and application scope) have been reset.")))

(defn delete [rc]
  (delete-user-by-id (to-long (:id rc)))
  (redirect rc "/user/list"))

(defn form [rc]
  (let [user (get-user-by-id (to-long (:id rc)))]
    (assoc rc :user user
           :departments (get-departments))))

(defn list [rc]
  (let [users (get-users)
        add-department (fn [u]
                         (assoc u :department
                                (:name (get-department-by-id (:department-id u)))))]
    (assoc rc :users (map add-department users))))

(defn save [rc]
  (let [{:keys [id first-name last-name email department-id]} rc]
    (save-user {:id (to-long id) :first-name first-name :last-name last-name :email email :department-id (to-long department-id)}))
  (redirect rc "/user/list"))

;; view methods -- only used for Enlive version of FW/1:

(defn default-view [rc nodes]
  (at nodes
      [:p#message]        (content (:message rc))
      [:p#reload-message] (if (:reload-message rc)
                            (content (:reload-message rc))
                            (substitute ""))))

(defn form-view [rc nodes]
  (let [user (:user rc)]
    (at nodes
        [:input#id] (set-attr :value (or (:id user) 0))
        [:input#first-name] (set-attr :value (:first-name user))
        [:input#last-name] (set-attr :value (:last-name user))
        [:input#email] (set-attr :value (:email user))
        [:select#department-id :option]
        (clone-for [dept (get-departments)]
                   (do->
                     (set-attr :value (:id dept))
                     (if (= (:department-id user) (:id dept))
                       (set-attr :selected "selected")
                       identity)
                     (content (:name dept)))))))

(defn list-view [rc nodes]
  (let [users (:users rc)]
    (at nodes
        [:tr.zero] (if (empty? users) identity (substitute ""))
        [:tr.user]
        (clone-for [user users]
                   [:td.id :a]
                   (do->
                    (append-attr :href (:id user))
                    (content (str (:id user))))
                   [:td.name :a]
                   (do->
                    (append-attr :href (:id user))
                    (content (str (:first-name user) " " (:last-name user))))
                   [:td.email] (content (:email user))
                   [:td.department] (content (:department user))
                   [:td.delete :a] (append-attr :href (:id user))))))
