(ns fschema.core-test
  (:require [clojure.test :refer :all]
            [fschema.core :refer :all]
            [fschema.constraints :as v]))

(defschema S1
  :a not-nil
  :b v/number?
  :c [v/not-nil v/string? (v/re-matches #"a.*c")]
  :d [v/not-nil (validate-each-with v/number? (v/> 5))])

(deftest test-schema
  (is (= (S1 nil)
         [{:error-id :not-nil, :message "Required value missing or nil", :value nil}]))
  (is (= (apply hash-set (S1 {}))
         #{{:path [:a], :error-id :not-nil,
            :message "Required value missing or nil", :value nil}
           {:path [:c], :error-id :not-nil,
            :message "Required value missing or nil", :value nil}
           {:path [:d], :error-id :not-nil,
            :message "Required value missing or nil", :value nil}}))
  (let [v {:a 5 :c "abc" :d [6 7 8]}]
    (is (identical? (S1 v) v))))

(def M1 (->mutator
         {:a str :b #(format "%d" %)}))

(defn test-read-mutator [x]
  (is (= ((->mutator pr-str read-string) x) x)))

(deftest test-mutator
  (is (= (M1 {:a 5 :b 7}) {:a "5", :b "7"}))
  (is (= ((->mutator
           M1
           pr-str)
           {:a 7})
         "{:a \"7\"}"))
  (test-read-mutator 5)
  (test-read-mutator "abc")
  (test-read-mutator {:a [1 "x"]}))



