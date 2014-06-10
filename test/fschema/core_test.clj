(ns fschema.core-test
  (:require [clojure.test :refer :all]
            [fschema.core :refer :all]
            [fschema.constraints :as v]
            [criterium.core :as crit]))

(defschema S1
  :a v/not-nil
  :b v/number?
  :c [v/not-nil v/string? (v/re-matches #"a.*c")]
  :d [v/not-nil (veach v/number? (v/> 5))])

(deftest test-schema
  (is (= (S1 nil)
         [{:error-id :fschema.core/not-nil, :message "Required value missing or nil", :value nil}]))
  (is (= (apply hash-set (S1 {}))
         #{{:path [:a], :error-id :fschema.core/not-nil,
            :message "Required value missing or nil", :value nil}
           {:path [:c], :error-id :fschema.core/not-nil,
            :message "Required value missing or nil", :value nil}
           {:path [:d], :error-id :fschema.core/not-nil,
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


(def veach1 (veach (v/> 5) (v/< 10)))

(def meach1 (meach (v/> 5) (v/< 10)))

(def v1 [6 7 8 9])

(def v2 [14 3 8 12 5])

(def bench1 []
  )

(def v1 (->validator
         {:a v/not-nil
          :b v/number?
          :c [v/not-nil v/string? (v/re-matches #"a.*c")]
          :d [v/not-nil (veach v/number? (v/> 5))]}))

(def m1 (->mutator
         {:a v/not-nil
          :b v/number?
          :c [v/not-nil v/string? (v/re-matches #"a.*c")]
          :d [v/not-nil (veach v/number? (v/> 5))]}))

(def d1 {:a 5 :c "abc" :d [6 7 8]})

;; (defn bench2 []
;;   (time
;;    (dotimes [i 100000]
;;      (v1 d1)))
;;   (time
;;    (dotimes [i 100000]
;;      (m1 d1))))
