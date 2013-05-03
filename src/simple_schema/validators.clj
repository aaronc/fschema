(ns simple-schema.validators
  (:use [simple-schema.core :exclude [not-nil]])
  (:refer-clojure :exclude [> < <= >= string? number?
                            map? vector? seq re-matches]))

;;(def not-nil simple-schema.core/not-nil)

(defconstraint > [x] (fn [y] (clojure.core/> y x)))

(defconstraint < [x] (fn [y] (clojure.core/< y x)))

(defconstraint >= [x] (fn [y] (clojure.core/>= y x)))

(defconstraint <= [x] (fn [y] (clojure.core/<= y x)))

(defconstraint re-matches [r] (fn [x] (clojure.core/re-matches r x)))

(defconstraint string? clojure.core/string?)

(defconstraint number? clojure.core/number?)

(defconstraint map? clojure.core/map?)

(defconstraint vector? clojure.core/vector?)

(defconstraint seq clojure.core/seq)

(defconstraint boolean? (fn [x] #{true false} x))

(defconstraint count= [n] (fn [coll] (= (count coll) n)))

(defconstraint count> [n] (fn [coll] (> (count coll) n)))

(defconstraint count< [n] (fn [coll] (< (count coll) n)))

(defconstraint count<= [n] (fn [coll] (<= (count coll) n)))

(defconstraint count>= [n] (fn [coll] (>= (count coll) n)))
