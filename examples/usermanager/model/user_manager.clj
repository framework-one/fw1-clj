(ns usermanager.model.user-manager
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]))

;; our database connection and initial data

(def ^:private my-db
  "Apache Derby database connection spec."
  {:dbtype "derby" :dbname "fw1_usermanager" :create true})

(def ^:private departments
  "List of departments, in memory since it doesn't change."
  [{:id 1 :name "Accounting"}
   {:id 2 :name "Sales"}
   {:id 3 :name "Support"}
   {:id 4 :name "Development"}])

(def ^:private department-by-id
  "Lookup table from department ID to department record."
  (reduce (fn [m d] (assoc m (:id d) d)) {} departments))

(def ^:private initial-user-data
  "Seed the database with this data."
  [{:first-name "Sean" :last-name "Corfield"
    :email "sean@worldsingles.com" :department-id 4}])

;; database initialization

(def ^:private address-book-schema
  "The SQL schema of the addressbook table. This is specific to
  Apache Derby, so update it if you use a different database."
  [[:id            :integer "not null generated always as identity"]
   [:first-name    "varchar(32)"]
   [:last-name     "varchar(32)"]
   [:email         "varchar(64)"]
   [:department-id :integer "not null"]])

(defn ->underscore
  "Naming strategy for entities in the database."
  [s]
  (str/replace s #"-" "_"))

(defn ->hyphen
  "Naming strategy for identifiers in our code.
  Should complement ->underscore above."
  [s]
  (-> s
      (str/lower-case)
      (str/replace #"_" "-")))

(defn setup-database
  "Called at application startup. Attempts to create the
  database table and populate it. Takes no action if the
  database table already exists."
  []
  (try
    (jdbc/db-do-commands my-db
                         (jdbc/create-table-ddl :addressbook
                                                address-book-schema
                                                {:entities ->underscore}))
    (println "Created database and addressbook table!")
    ;; if table creation was successful, it didn't exist before
    ;; so populate it...
    (try
      (jdbc/insert-multi! my-db :addressbook initial-user-data {:entities ->underscore})
      (println "Populated database with initial data!")
      (catch Exception e
        (println "Unable to populate the initial data -- proceed with caution!")))
    (catch Exception _
      (println "Looks like the database is already setup!"))))

(defn get-department-by-id
  "Given a department ID, return the department record.
  Uses in-memory lookup for non-changing data."
  [id]
  (get department-by-id id))

(defn get-departments
  "Return all available department records (in order)."
  []
  departments)

(defn get-user-by-id
  "Given a user ID, return the user record."
  [id]
  (jdbc/get-by-id my-db :addressbook id {:identifiers ->hyphen}))

(defn get-users
  "Return all available users, sorted by name."
  []
  (jdbc/query my-db
              ["select * from addressbook order by last_name, first_name"]
              {:identifiers ->hyphen}))

(defn save-user
  "Save a user record. If ID is present and not zero, then
  this is an update operation, otherwise it's an insert."
  [user]
  (let [id (:id user)]
    (if (and id (not (zero? id)))
      ;; update
      (jdbc/update! my-db :addressbook (dissoc user :id) ["id = ?" id]
                    {:entities ->underscore})
      ;; insert
      (jdbc/insert! my-db :addressbook (dissoc user :id)
                    {:entities ->underscore}))))

(defn delete-user-by-id
  "Given a user ID, delete that user."
  [id]
  (jdbc/delete! my-db :addressbook ["id = ?" id]
                {:entities ->underscore}))
