(ns simple-schema.validators
  (:use [simple-schema.core]))

(defvalidator v> [x] (fn [y] (> y x)))

(defvalidator v< [x] (fn [y] (< y x)))

(defvalidator v>= [x] (fn [y] (>= y x)))

(defvalidator v<= [x] (fn [y] (<= y x)))

(defvalidator vregex [r] (fn [x] (re-matches r x)))

(defvalidator vstring string?)

(defvalidator vnumber number?)

(defvalidator vmap map?)

(defvalidator vvector vector?)

