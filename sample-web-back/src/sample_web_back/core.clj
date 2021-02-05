(ns sample-web-back.core
  (:require [sample-web-back.backdb :refer [mk-routes] :as back]
            [compojure.api.sweet :refer [GET api routes]]
            [compojure.route :as croutes]
            [sample-web-back.exceptions :as exceptions]
            [sample-web-back.healthcheck :as health]))

(defn app [db]
  (api
   {:exceptions {:handlers exceptions/default-handlers}}
   (routes (mk-routes db)
           (GET health/health-route []
             (health/health-handler)))))

(try (back/run-new-db back/db)
     (back/run-triggers) 
     (catch Exception e (str "caught exception: " (.getMessage e))))

(defn not-found []
  (croutes/not-found {:error "No matching endpoint"}))

(def handler
  (routes app
          (api (not-found))))
