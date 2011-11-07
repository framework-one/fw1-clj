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
(defn- get-user-by-id [id]
  (first (filter #(== id (:id %)) @users)))

(defn- to-long [l]
  (try (Long/parseLong l) (catch Exception _ 0)))

;; controller methods
(defn default [rc]
  (assoc rc :message "Welcome to the Framework One User Manager application demo!"))

(defn form [rc]
  ;; fetch user based on id
  (assoc rc :user (get-user-by-id (to-long (:id rc)))))

(defn list [rc]
  rc)

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

(defn form-list [rc nodes]
  nodes)