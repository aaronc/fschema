(ns fschema.constraints
  (:require
   [fschema.core.constraint :refer [tag-constraint defconstraint constraint]]
   [fschema.error :refer [error error?]])
  (:refer-clojure :exclude [> < <= >= string? number?
                            map? vector? seq re-matches
                            keyword? symbol?]))

(def not-nil
  (let [attrs {:error-id ::not-nil
                :message "Required value missing or nil"}]
    (tag-constraint
      (fn not-nil [x]
        (if (nil? x)
          (error (assoc attrs :value nil))
          x))
      attrs)))

(defconstraint any identity)

(defconstraint > [x] (fn [y] (clojure.core/> y x)))

(defconstraint < [x] (fn [y] (clojure.core/< y x)))

(defconstraint >= [x] (fn [y] (clojure.core/>= y x)))

(defconstraint <= [x] (fn [y] (clojure.core/<= y x)))

(defconstraint string? clojure.core/string?)

(defconstraint number? clojure.core/number?)

(defconstraint map? clojure.core/map?)

(defconstraint vector? clojure.core/vector?)

(defconstraint seq? clojure.core/seq)

(defconstraint keyword? clojure.core/keyword?)

(defconstraint symbol? clojure.core/symbol?)

(defconstraint boolean? (fn [x] #{true false} x))

(defn re-matches
  [r]
  (let [c (constraint :re-matches (fn [x] (clojure.core/re-matches r x))
                      :params [r])]
    (fn [x]
      (if-let [err (error? (string? x))]
        err
        (c x)))))

(defconstraint count= [n] (fn [coll] (clojure.core/= (count coll) n)))

(defconstraint count> [n] (fn [coll] (clojure.core/> (count coll) n)))

(defconstraint count< [n] (fn [coll] (clojure.core/< (count coll) n)))

(defconstraint count<= [n] (fn [coll] (clojure.core/<= (count coll) n)))

(defconstraint count>= [n] (fn [coll] (clojure.core/>= (count coll) n)))
