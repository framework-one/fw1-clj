(ns example.controllers.user
  (:use framework.one))

;; mock data
(def ^:private departments
  [{:id 1 :name "Accounting"}
   {:id 2 :name "Sales"}
   {:id 3 :name "Support"}
   {:id 4 :name "Development"}])

(def ^:private initial-user-data
  [{:id 1 :first-name "Sean" :last-name "Corfield" :email "sean@worldsingles.com" :department-id 3}])

(def ^:private users (atom initial-user-data))

;; data access methods - would be in a service
(defn- get-department-by-id [id]
  (first (filter #(== id (:id %)) departments)))

(defn- get-user-by-id [id]
  (first (filter #(== id (:id %)) @users)))

(defn- to-long [l]
  (try (Long/parseLong l) (catch Exception _ 0)))

;; controller methods
(defn default [rc]
  (assoc rc :message "Welcome to the Framework One User Manager application demo!"))

(defn delete [rc]
  ;; need redirect!
  )

(defn form [rc]
  ;; fetch user based on id
  (assoc rc :user (get-user-by-id (to-long (:id rc)))))

(defn list [rc]
  (assoc rc :users @users))

(defn save [rc]
  ;; need redirect!
  )

;; view methods
(defn default-view [rc nodes]
  (at nodes
      [:p#message] (content (:message rc))
      [:p#reload] (if (:reload rc)
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
        (clone-for [dept departments]
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