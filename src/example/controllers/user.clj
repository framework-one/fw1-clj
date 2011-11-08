(ns example.controllers.user
  (:use framework.one)
  (:use example.model.user-manager))

;; controller methods
(defn default [rc]
  (assoc rc :message "Welcome to the Framework One User Manager application demo!"))

(defn delete [rc]
  (delete-user-by-id (to-long (:id rc)))
  (redirect rc "/user/list"))

(defn form [rc]
  (let [user (get-user-by-id (to-long (:id rc)))]
    (assoc rc :user user)))

(defn list [rc]
  (let [users (get-users)]
    (assoc rc :users users)))

(defn save [rc]
  (let [{:keys [id first-name last-name email department-id]} rc]
    (save-user {:id (to-long id) :first-name first-name :last-name last-name :email email :department-id (to-long department-id)}))
  (redirect rc "/user/list"))

;; view methods
(defn default-view [rc nodes]
  (at nodes
      [:p#message] (content (:message rc))
      [:p#reload] (if (reload? rc)
                    (content "The framework cache (and application scope) have been reset.")
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
                   [:td.department] (content (:name (get-department-by-id (:department-id user))))
                   [:td.delete :a] (append-attr :href (:id user))))))
