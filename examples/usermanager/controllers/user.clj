(ns usermanager.controllers.user
  (:require [framework.one :refer :all]
            [usermanager.model.user-manager :refer :all]))

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

