(ns simple-schema.core-test
  (:require [clojure.test :refer :all]
            [simple-schema.core :refer :all]
            [simple-schema.validators :as v]))

(defschema S1
  :a not-nil
  :b vnumber
  :c [not-nil vstring (vregex #"a.*c")]
  :d [not-nil (veach [vnumber (v> 5)])])

(deftest test-schema
  (is (= (validate S1 nil)
         [{:code :simple-schema.core/required, :message "Required value missing or nil", :value nil}]))
  (is (= (validate S1 {})
         [{:path '(:a), :code :simple-schema.core/required, :message "Required value missing or nil", :value nil}
          {:path '(:c), :code :simple-schema.core/required, :message "Required value missing or nil", :value nil}
          {:path '(:d), :code :simple-schema.core/required, :message "Required value missing or nil", :value nil}]))
  (is (nil? (validate S1 {:a 5 :c "abc" :d [6 7 8]}))))

(def M1 (->mutator
         {:a str :b #(format "%d" %)}))

(defn test-read-mutator [x]
  (is (= (mutate (->mutator pr-str read-string) x) x)))

(deftest test-mutator
  (is (= (mutate M1 {:a 5 :b 7}) {:a "5", :b "7"}))
  (is (= (mutate (->mutator
                  M1
                  pr-str)
                 {:a 7})
         "{:a \"7\"}"))
  (test-read-mutator 5)
  (test-read-mutator "abc")
  (test-read-mutator {:a [1 "x"]}))



