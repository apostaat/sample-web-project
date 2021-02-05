(ns sample-web-back.test-util
  (:require [ring.mock.request :as mock]
            [cheshire.core :as json]))

(defn json-body-enc [request body]
  (-> request
      (mock/content-type "application/json")
      (mock/body (json/generate-string body {:escape-non-ascii true}))))



(defn response [resp]
  (let [{:keys [status body]} resp
        body-processed (cond
                         (nil? body) nil
                         (string? body) (try
                                          (json/parse-string body)
                                          (catch Exception _
                                            body))
                         :else (let [body-content (slurp body)]
                                 (try
                                   (json/parse-string  body-content true)
                                   (catch Exception _
                                     body-content))))
        errors (or (:errors body-processed) (:error body-processed))
        status-explained (cond
                           (#{200 201 301 304} status) status
                           errors [status errors (:value body-processed)]
                           :else [status body-processed])]
    {:status status
     :status+ status-explained
     :body body-processed
     :headers (:headers resp)}))

