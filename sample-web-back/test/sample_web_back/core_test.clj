(ns sample-web-back.core-test
  (:require
   [sample-web-project.core :refer app]
   [sample-web-project.backdb :as back]
   [schema.core :as s]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]
   [sample-web-project.test-util :as tu]))

(use-fixtures :each back/fix-clean-db)

(deftest pure-data-test
  (testing "data validation is correct"
    (is (= "355-355-349-55" (s/validate back/InsuranceNo "355-355-349-55")))
    (is (= "355 355 349 55" (s/validate back/InsuranceNo "355 355 349 55")))
    (is (= "35535534955" (s/validate back/InsuranceNo "35535534955")))
    (is (= "2001-12-27" (s/validate back/DateInput "2001-12-27")))))

(deftest database-test
  (testing "we can insert,modify and delete patients"

    (let [body {:insurance_no "355-355-349-55"
                :fullname "Sergeev Victor Petrovich"
                :gender "M"
                :birth_date "2001-12-27"
                :address "NY, South Harlem, Hip Hop street, house 13"}
          post-new (-> (mock/request :post "/sample-web-project/patient")
                       (tu/json-body-enc body)
                       app
                       (tu/response))]
      (is (= 200 (:status+ post-new)))
      (is (= (assoc (dissoc body :birth_date) :insurance_no "35535534955")
             (dissoc (first (:body post-new)) :birth_date))))

    (let [post-existing (-> (mock/request :post "/sample-web-project/patient")
                            (tu/json-body-enc {:insurance_no "355-355-349-55"
                                               :fullname "Sergeev Victor Petrovich"
                                               :gender "M"
                                               :birth_date "2001-12-27"
                                               :address "NY, South Harlem, Hip Hop street, house 13"})
                            app
                            (tu/response))]
      (is (= 400 (:status post-existing)))
      (is (= "Patient with such insurance id is already exists." (:_message (:body post-existing)))))
    (let [update-existing (-> (mock/request :put "/sample-web-project/patient")
                              (tu/json-body-enc {:insurance_no "355-355-349-55"
                                                 :fullname "Cardi B"
                                                 :gender "F"})
                              app
                              (tu/response))]
      (is (= 200 (:status+ update-existing)))
      (is (= {} (:body update-existing))))
    (let [update-non-existing (-> (mock/request :put "/sample-web-project/patient")
                                  (tu/json-body-enc {:insurance_no "355-355-349-50"
                                                     :fullname "Cardi B"
                                                     :gender "F"})
                                  app
                                  (tu/response))]
      (is (= 400 (:status update-non-existing)))
      (is (= "Patient with such insurance id does not exist." (:_message (:body update-non-existing)))))
    (let [get-all (-> (mock/request :get "/sample-web-project/patient")
                      app
                      (tu/response))]
      (is (= 200 (:status get-all)))
      (is (= {:insurance_no "35535534955"
              :fullname "Cardi B"
              :gender "F"
              :address "NY, South Harlem, Hip Hop street, house 13"
              :birth_date "2001-12-26"} (first (:body get-all)))))
    (let  [delete-non-existing (-> (mock/request :delete "/sample-web-project/patient")
                                   (tu/json-body-enc {:insurance_no "355-355-349-50"})
                                   app
                                   (tu/response))]
      (is (= 400 (:status delete-non-existing)))
      (is (= "Patient with such insurance id does not exist." (:_message (:body delete-non-existing)))))
    (let  [delete-existing (-> (mock/request :delete "/sample-web-project/patient")
                               (tu/json-body-enc {:insurance_no "355-355-349-55"})
                               app
                               (tu/response))]
      (is (= 200 (:status delete-existing)))
      (is (= {} (:body delete-existing))))))
