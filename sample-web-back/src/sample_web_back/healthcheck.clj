(ns sample-web-back.healthcheck
  (:require [sample-web-project.backdb :as db]))

(def health-route "/health")

(defn check-health []
  (let [db-result (try (db/read-all-db)
                       :ok
                       (catch Exception _
                         :fail))]
    {:overall db-result
     :db db-result}))

(defn health-handler []
  (let [health (check-health)]
    {:status (if (= :ok (:overall health)) 200 500)
     :body health}))