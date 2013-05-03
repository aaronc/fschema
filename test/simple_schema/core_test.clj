(ns simple-schema.core-test
  (:require [clojure.test :refer :all]
            [simple-schema.core :refer :all]
            [simple-schema.validators :as v]))

(defschema S1
  :a not-nil
  :b v/number?
  :c [not-nil v/string? (v/re-matches #"a.*c")]
  :d [not-nil (validate-each-with v/number? (v/> 5))])

(deftest test-schema
  (is (= (S1 nil)
         [{:error-id :not-nil, :message "Required value missing or nil", :value nil}]))
  (is (= (S1 {})
         [{:path '(:a), :error-id :not-nil, :message "Required value missing or nil", :value nil}
          {:path '(:c), :error-id :not-nil, :message "Required value missing or nil", :value nil}
          {:path '(:d), :error-id :not-nil, :message "Required value missing or nil", :value nil}]))
  (is (not (errors? (S1 {:a 5 :c "abc" :d [6 7 8]})))))

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



