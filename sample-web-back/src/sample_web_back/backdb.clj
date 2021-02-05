(ns sample-web-back.backdb
  (:require [clojure.java.jdbc :as db]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [schema.core :as s]
            [schema-tools.core :as st]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [compojure.api.sweet :refer [defroutes context GET PUT DELETE POST]]))

(defn insurance-validate [ins-no] (re-matches #"^\d{3}[\s-]{0,1}\d{3}[\s-]{0,1}\d{3}[\s-]{0,1}\d{2}" ins-no))

(defn date-input-validate [date] (re-matches #"^\d{4}[-]\d{1,2}[-]\d{1,2}" date))

(defn date-output-validate [date] (= java.sql.Date (type date)))

(s/defschema Gender (s/enum "F" "M" "O"))

;I'm not that conservative, but taking into account all existing in 2k20 genders makes my task little bit complicated

(s/defschema InsuranceNo  (s/pred insurance-validate s/Str))

(s/defschema DateInput (s/pred date-input-validate s/Str))

(s/defschema DateOutput (s/pred date-output-validate))

(s/defschema ClientInfoInput {:insurance_no InsuranceNo
                               :fullname s/Str
                               :gender Gender
                               :birth_date DateInput
                               :address s/Str})

(s/defschema ClientInfoOutput (st/merge (-> ClientInfoInput
                                             (st/select-keys [:insurance_no :fullname :gender :time :address]))
                                         {:birth_date DateOutput}))

(s/defschema ClientInfoOutputGet (st/merge (-> ClientInfoInput
                                                (st/select-keys [:insurance_no :fullname :gender :time :address]))
                                            {:birth_date DateInput}))

(s/defschema ClientInfoUpdate (-> ClientInfoInput
                                   (st/select-keys [:insurance_no :fullname :gender :time :birth_date :address])
                                   (st/optional-keys [:fullname :gender :time :birth_date :address])))

(s/defschema ClientInfoDelete {:insurance_no InsuranceNo})

(def db
  {:dbtype "postgresql"
   :dbname (env :dbname)
   :host "localhost"
   :user (env :user)
   :password (env :password)})

(def db-test
  {:dbtype "postgresql"
   :dbname "test"
   :host "localhost"
   :user (env :user)
   :password (env :password)})

(def health-sql (db/create-table-ddl :clients [[:insurance_no "VARCHAR(15)" "PRIMARY KEY"]
                                                [:fullname "VARCHAR(50)"]
                                                [:gender "VARCHAR(1)"]
                                                [:birth_date :date]
                                                [:address "VARCHAR(100)"]]))

(def backup-sql (db/create-table-ddl :clients_backup [[:id "SERIAL"]
                                                       [:insurance_no "VARCHAR(15)"]
                                                       [:fullname "VARCHAR(50)"]
                                                       [:gender "VARCHAR(1)"]
                                                       [:birth_date :date]
                                                       [:address "VARCHAR(100)"]]))

(def create-pseudodelete-trigger-func
  "CREATE OR REPLACE FUNCTION pseudodelete() RETURNS trigger AS $function$ BEGIN
  INSERT INTO clients_backup(insurance_no, fullname, gender, birth_date, address) 
  VALUES(OLD.insurance_no, OLD.fullname, OLD.gender, OLD.birth_date, OLD.address);
  RETURN OLD;
  END;
  $function$ 
  LANGUAGE plpgsql;")

(def create-pseudodelete-backup-trigger
  "CREATE TRIGGER row_pseudodelete
   AFTER DELETE ON clients
   FOR EACH ROW
   EXECUTE PROCEDURE pseudodelete();")

(def custom-formatter (tf/formatter "yyyy-MM-dd"))

(defn time-to-sql-convert [time] (tc/to-sql-date (tf/parse custom-formatter time)))

(defn time-from-sql-convert [time] (tf/unparse custom-formatter (tc/from-sql-date time)))

(defn transform-insurance-no [ins-no]
  (str/join (str/split ins-no #"\s|-")))

(defn run-new-db [db]
  (db/db-do-commands db [health-sql
                         backup-sql]))

(defn run-triggers []
  (db/db-do-commands db [create-pseudodelete-trigger-func
                         create-pseudodelete-backup-trigger]))

(defn fix-clean-db [t]
  (db/db-do-commands db ["TRUNCATE TABLE clients"
                         "TRUNCATE TABLE clients_backup"])
  (t))

(defn check-insurance-add [insurance-no]
  (when-not (empty? (db/query db ["SELECT * FROM clients WHERE insurance_no = ?" insurance-no]))
    (throw (ex-info "Client with such insurance id is already exists." {:type :id-exists
                                                                         :reason "id-exists"}))))

(defn check-insurance-update [insurance-no]
  (when (empty? (db/query db ["SELECT * FROM clients WHERE insurance_no = ?" insurance-no]))
    (throw (ex-info "Client with such insurance id does not exist." {:type :does-not-exist
                                                                      :reason "does-not-exist"}))))

(defn insert-client-db
  [db body]
  (let [format-ins-no (transform-insurance-no (:insurance_no body))
        format-bday (time-to-sql-convert (:birth_date body))]
    (check-insurance-add format-ins-no)
    (db/insert! db :clients (assoc body
                                    :insurance_no format-ins-no
                                    :birth_date format-bday))
    (db/query db ["SELECT * FROM clients WHERE insurance_no = ?" format-ins-no])))

(defn create-update-field [body]
  (if-let [bday (:birth_date body)]
    (assoc (dissoc body :insurance_no) :birth_date (time-to-sql-convert bday))
    (dissoc body :insurance_no)))

(defn update-client-db
  [body]
  (let [format-ins-no (transform-insurance-no (:insurance_no body))
        updated-info (create-update-field body)]
    (check-insurance-update format-ins-no)
    (cond (= 1 (first (db/update! db :clients updated-info ["insurance_no = ?" format-ins-no]))) {}
          :else (throw (ex-info "Database input failed." {:type :failed
                                                          :reason "database-input-failed"})))))

(defn safe-delete-client-db [insurance-no]
  (let [format-ins-no (transform-insurance-no (:insurance_no insurance-no))]
    (check-insurance-update format-ins-no)
    (cond (not= 1 (db/delete! db :clients ["insurance_no = ?" format-ins-no])) {}
          :else (throw (ex-info "Database input failed." {:type :failed
                                                          :reason "database-input-failed"})))))
(defn read-all-db []
  (let [db-response (db/query db ["SELECT * FROM clients"])]
    (mapv (fn [x]
            (assoc x :birth_date (time-from-sql-convert (:birth_date x))))
          db-response)))


(defn mk-routes [db]

  (defroutes sample-web-project-routes

    (context "/sample-web-project" []

      :tags ["sample-web-project-routes"]

      (GET "/client" req
        :summary "Get all clients info"
        :return [ClientInfoOutputGet]

        {:status 200
         :body (read-all-db)})

      (GET "/insurance-add" req
        :summary "Checks if insurance is in database"
        :query-params [number :- InsuranceNo]

        {:status 200
         :body (check-insurance-add (transform-insurance-no number))})

      (GET "/insurance-up" req
        :summary "Checks if insurance is in database"
        :query-params [number :- InsuranceNo]

        {:status 200
         :body (check-insurance-update (transform-insurance-no number))})

      (POST "/client" req
        :summary "Add client to database"
        :body [body ClientInfoInput]
        :return [ClientInfoOutput]
        {:status 200
         :body (insert-client-db db body)})

      (PUT "/client" req
        :summary "Modify client info"
        :body [body ClientInfoUpdate]
        :return {}
        {:status 200
         :body (update-client-db body)})

      (DELETE "/client" req
        :summary "Delete client info"
        :body [body ClientInfoDelete]
        :return {}
        {:status 200
         :body (safe-delete-client-db body)}))))