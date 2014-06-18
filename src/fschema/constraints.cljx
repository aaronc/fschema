(ns fschema.constraints
  (:require
   [fschema.core.constraint :refer [tag-constraint defconstraint constraint]]
   [fschema.error :refer [error error?]])
  (:refer-clojure :exclude [> < <= >= string? number?
                            map? vector? seq re-matches
                            keyword? symbol? set? integer?
                            seq? = not=]))

(def not-nil fschema.core.constraint/not-nil)

(defconstraint any (constantly true))

(defconstraint string? clojure.core/string?)

(defconstraint number? clojure.core/number?)

(defconstraint integer? clojure.core/integer?)

(defconstraint map? clojure.core/map?)

(defconstraint vector? clojure.core/vector?)

(defconstraint seq? clojure.core/seq)

(defconstraint keyword? clojure.core/keyword?)

(defconstraint symbol? clojure.core/symbol?)

(defconstraint set? clojure.core/set?)

(defconstraint boolean? (fn [x] #{true false} x))

(defconstraint = [x] (fn [y] (clojure.core/= y x))
  :pre-constraint number?)

(defconstraint not= [x] (fn [y] (clojure.core/not= y x))
  :pre-constraint number?)

(defconstraint > [x] (fn [y] (clojure.core/> y x))
  :pre-constraint number?)

(defconstraint < [x] (fn [y] (clojure.core/< y x))
  :pre-constraint number?)

(defconstraint >= [x] (fn [y] (clojure.core/>= y x))
  :pre-constraint number?)

(defconstraint <= [x] (fn [y] (clojure.core/<= y x))
  :pre-constraint number?)

(defconstraint re-matches [r] (fn [x] (clojure.core/re-matches r x))
  :pre-constraint string?)

(defconstraint count= [n] (fn [coll] (clojure.core/= (count coll) n)))

(defconstraint count> [n] (fn [coll] (clojure.core/> (count coll) n)))

(defconstraint count< [n] (fn [coll] (clojure.core/< (count coll) n)))

(defconstraint count<= [n] (fn [coll] (clojure.core/<= (count coll) n)))

(defconstraint count>= [n] (fn [coll] (clojure.core/>= (count coll) n)))
