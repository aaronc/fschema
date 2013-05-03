(ns simple-schema.validators
  (:use [simple-schema.core :exclude [not-nil]])
  (:refer-clojure :exclude [> < <= >= string? number?
                            map? vector? seq re-matches]))

;;(def not-nil simple-schema.core/not-nil)

(defvalidator > [x] (fn [y] (clojure.core/> y x)))

(defvalidator < [x] (fn [y] (clojure.core/< y x)))

(defvalidator >= [x] (fn [y] (clojure.core/>= y x)))

(defvalidator <= [x] (fn [y] (clojure.core/<= y x)))

(defvalidator re-matches [r] (fn [x] (clojure.core/re-matches r x)))

(defvalidator string? clojure.core/string?)

(defvalidator number? clojure.core/number?)

(defvalidator map? clojure.core/map?)

(defvalidator vector? clojure.core/vector?)

(defvalidator seq clojure.core/seq)

(defvalidator boolean? (fn [x] #{true false} x))

(defvalidator count= [n] (fn [coll] (= (count coll) n)))

(defvalidator count> [n] (fn [coll] (> (count coll) n)))

(defvalidator count< [n] (fn [coll] (< (count coll) n)))

(defvalidator count<= [n] (fn [coll] (<= (count coll) n)))

(defvalidator count>= [n] (fn [coll] (>= (count coll) n)))
