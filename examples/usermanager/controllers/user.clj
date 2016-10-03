(ns usermanager.controllers.user
  (:require [framework.one :refer :all]
            [usermanager.model.user-manager :refer :all]))

(def ^:private changes
  "Count the number of changes (since the last reload)."
  (atom 0))

(defn after [rc]
  (assoc rc :changes @changes))

(defn default [rc]
  (assoc rc
         :message        (str "Welcome to the Framework One User Manager application demo! "
                              (pr-str (-> (event rc :config) :application)))
         :reload-message (when (reload? rc)
                           "The framework cache (and application scope) have been reset.")))

(defn delete [rc]
  (swap! changes inc)
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
  (swap! changes inc)
  (let [{:keys [id first-name last-name email department-id]} rc]
    (save-user {:id (to-long id) :first-name first-name :last-name last-name
                :email email :department-id (to-long department-id)}))
  (redirect rc "/user/list"))
