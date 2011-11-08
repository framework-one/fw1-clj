(ns usermanager.model.user-manager)

;; mock data
(def ^:private departments
  [{:id 1 :name "Accounting"}
   {:id 2 :name "Sales"}
   {:id 3 :name "Support"}
   {:id 4 :name "Development"}])

(def ^:private initial-user-data
  [{:id 1 :first-name "Sean" :last-name "Corfield" :email "sean@worldsingles.com" :department-id 4}])

(def ^:private users (atom initial-user-data))

(defn- new-id [rows]
  (if (empty? rows) 1 (inc (:id (apply max-key :id rows)))))

(defn get-department-by-id [id]
  (first (filter #(= id (:id %)) departments)))

(defn get-departments []
  departments)

(defn get-user-by-id [id]
  (first (filter #(= id (:id %)) @users)))

(defn get-users []
  (sort-by :id @users))

(defn save-user [user]
  (let [id (:id user)]
    (if (zero? id)
      ;; insert
      (swap! users #(conj % (assoc user :id (new-id %))))
      ;; update
      (swap! users #(map (fn [row] (if (= id (:id row)) user row)) %)))))

(defn delete-user-by-id [id]
  (swap! users (partial remove #(= id (:id %)))))
