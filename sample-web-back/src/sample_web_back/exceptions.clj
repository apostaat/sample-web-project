(ns sample-web-back.exceptions
  (:require [clojure.tools.logging :as log]))

(defn create-handler [status]
  (fn error-response [e _ _]
    {:status status
     :body (merge {:_message (.getMessage e)} (ex-data e))
     :headers {}}))

(defn with-logging
  ([handler]
   (with-logging handler :error))
  ([handler level]
   (fn handler-with-logging [e data req]
     (log/logp level e (.getMessage e) (ex-data e))
     (handler e data req))))

(def default-handlers
  {:does-not-exist (with-logging (create-handler 400) :warn)
   :id-exists (with-logging (create-handler 400) :warn)})
