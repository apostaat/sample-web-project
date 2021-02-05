(ns sample-web-front.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [reagent.core :as r]
   [reagent.dom :as d]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [nano-id.core :refer [nano-id]]))

;npx shadow-cljs watch app 

(def insurance-tip (r/atom nil))

(def crud-result-tip (r/atom nil))

(def insurance-tip-el [:p {:style {:color "red"}}
                       "No letters allowed. You may use digits, spaces or dashes."])

(def style-succ {:style {:border "1px solid green"}})

(def style-fail {:style {:border "1px solid red"}})

(def insurance-input-style (r/atom {}))

(def form-state (r/atom {}))

(def availability (r/atom {}))

(def client-list (r/atom nil))

(def key-seq [:insurance_no
              :fullname
              :gender
              :address
              :birth_date])

(def data (r/atom nil))

(go (let
     [responce
      (<! (http/get "https://raw.githubusercontent.com/borkdude/sci/master/README.md"
                    {:with-credentials? false}))]
      (reset! data (str (responce :body)))))

(def db-add-root "api/sample-web-project/insurance-add")

(def db-upd-root "api/sample-web-project/insurance-up")

(def api-root "api/sample-web-project/client")

(def hide-button (r/atom nil))

(defn ins-no-validation-success [db-op-word]
  [:p {:style {:color "green"}}
   (str "Nice! There is " db-op-word "such client in DB.")])

(defn ins-no-validation-fail [db-op-word]
  [:p {:style {:color "red"}}
   (str "Oops! There is " db-op-word "such client in DB.")])

(defn crud-result-success [db-op-word]
  [:p {:style {:color "green"
               :font-size "small"}}
   (str db-op-word " succeeded.")])

(defn crud-result-fail [db-op-word status]
  [:p {:style {:color "red"
               :font-size "small"}}
   (str db-op-word " failed with status " status ".")])

(defn crud-result-tip-nil []
  (js/setTimeout #(reset! crud-result-tip nil) 3000))

(defn db-op-return []
  (let [e (js/document.getElementById "db-op")]
    (.-text (aget (.-options e) (.-selectedIndex e)))))

(defn dispatch-operation-by-type [db-op add-arg update-arg]
  (cond
    (= "Add" db-op) add-arg
    (or (= "Update" db-op)
        (= "Delete" db-op)) update-arg))

(defn db-root-manager [db-op]
  (dispatch-operation-by-type db-op db-add-root db-upd-root))

(defn db-op-word-manager [db-op]
  (dispatch-operation-by-type db-op ["no " nil] [nil "no "]))

(defn hide-unused-forms [db-op]
  (cond
    (= "Delete" db-op) (swap! availability assoc :display "none")
    (or (= "Update" db-op)
        (= "Add" db-op)) (reset! availability {})))

(defn create-table-heading []
  (reduce (fn [acc v] (conj acc (vector :th {:key (nano-id)} (-> v
                                                                 symbol
                                                                 str))))
          [:tr {:key (nano-id)}] key-seq))

(defn create-table-cell [cell]
  (apply conj [:tr {:key (nano-id)}] (for [x key-seq]
                                       (vector :th
                                               {:key (nano-id)}
                                               (x cell)))))

(defn create-table-cells [resp]
  (map create-table-cell resp))

(defn table-from-response [resp]
  [:table {:style {:border "1px dotted black"}}
   [:tbody (create-table-heading)
    (create-table-cells resp)]])

(defn read-all-client []
  (go (let [response (<! (http/get api-root))]
        (if (= 200 (:status response))
          (reset! client-list (table-from-response (:body response)))
          (reset! client-list (table-from-response [])))
        (reset! hide-button [:button {:type "button"
                                      :on-click #(do (reset! client-list nil)
                                                     (reset! hide-button nil))
                                      :style {:margin "3px"}} " Hide "]))))

(defn add-post-put-manager [db-op]
  (go
    (let [response (cond (= "Add" db-op) (<! (http/post api-root {:json-params @form-state}))

                         (= "Update" db-op) (<! (http/put api-root {:json-params @form-state}))

                         (= "Delete" db-op) (<! (http/delete api-root {:json-params (dissoc @form-state :fullname
                                                                                            :gender
                                                                                            :address
                                                                                            :birth_date)})))
          status (:status response)]
      (if (= 200 status)
        (reset! crud-result-tip (crud-result-success db-op))
        (reset! crud-result-tip (crud-result-fail db-op status)))
      (crud-result-tip-nil))))

(defn db-insurance-validate [input]
  (go (let [db-op (db-op-return)
            db-op-root (db-root-manager db-op)
            [db-op-word1 db-op-word2] (db-op-word-manager db-op)
            response (<! (http/get db-op-root
                                   {:query-params {"number" input}}))]
        (if (= 200 (:status response))
          (reset! insurance-tip
                  (ins-no-validation-success db-op-word1))
          (do (reset! insurance-tip
                      (ins-no-validation-fail db-op-word2))
              (swap! insurance-input-style merge style-fail))))))

(defn update-form-state [k v]
  (swap! form-state assoc k (-> v .-target .-value)))

(defn regex-insurance-validate [input]
  (let [c (count input)
        match (re-matches #"^(?:\d{3}[\s-]{0,1}(?:\d{3}[\s-]{0,1}(?:\d{3}[\s-]{0,1}(?:\d{2}|\d{0,2})|\d{0,3})|\d{0,3})|\d{0,3})"
                          input)]
    (if (and match (= (count match) c))
      (do (swap! insurance-input-style merge style-succ)
          (cond
            (and (= c 14)
                 (re-matches #"^\d{3}[\s-]{0,1}\d{3}[\s-]{0,1}\d{3}[\s-]{0,1}\d{2}" input)) (db-insurance-validate input)
            (and (= c 11)
                 (re-matches #"^\d{11}" input)) (db-insurance-validate input)
            :else (swap! insurance-input-style merge style-succ)))
      (do (swap! insurance-input-style merge style-fail)
          (reset! insurance-tip insurance-tip-el)))))

(defn home-page []

  [:div {:style {:border "3px dotted black"
                 :border-radius "20px"
                 :padding "10px"}}
   [:h5 "Megasystem v.1.0"]
   [:p "Choose type of database operation: " [:select
                                              {:default-value "Add"
                                               :id "db-op"
                                               :on-change #(do
                                                             (-> (js/document.getElementById "ins-no")
                                                                 .-value
                                                                 regex-insurance-validate)
                                                             (-> (db-op-return)
                                                                 hide-unused-forms))}
                                              [:option "Add"]
                                              [:option "Update"]
                                              [:option "Delete"]]]
   [:p " Social insurance number : "
    [:input (merge {:type "text" :name "insurance-no"
                    :on-click #(reset! insurance-tip nil)
                    :on-change #(do (reset! insurance-tip nil)
                                    (regex-insurance-validate (-> % .-target .-value))
                                    (update-form-state :insurance_no %))
                    :id "ins-no"} @insurance-input-style)]]
   [:div {:style {:font-size "small"}} @insurance-tip]
   [:div {:style @availability} [:p "Choose gender: "
                                 [:input {:type "radio" :name "gender" :value "F" :on-click #(update-form-state :gender %)}] [:span "Female"]
                                 [:input {:type "radio" :name "gender" :value "M" :on-click #(update-form-state :gender %)}] [:span "Male"]
                                 [:input {:type "radio" :name "gender" :value "O" :on-click #(update-form-state :gender %)}] [:span "Other"]]
    [:p " Fullname: " [:input {:type "text" :name "address" :on-change #(update-form-state :fullname %)}]]
    [:p " Address: " [:input {:type "text" :name "address" :on-change #(update-form-state :address %)}]]
    [:p " Date of birth: " [:input {:type "date" :name "birthdate" :on-change #(update-form-state :birth_date %)}]]]
   [:button {:type "button"
             :on-click #(read-all-client)} "Get all client data"]
   @hide-button
   [:input {:type "submit"
            :on-click #(do (.preventDefault %)
                           (add-post-put-manager (db-op-return)))}]
   @crud-result-tip
   [:div {:style {:font-size "small"}} @client-list]
   [:div @data]])

(defn mount-root []
  (d/render [home-page] (.getElementById js/document "app")))

(defn ^:export init! []
  (mount-root))