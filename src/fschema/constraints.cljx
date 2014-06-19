(ns fschema.constraints
  (:require
   [fschema.core.constraint :refer [tag-constraint defconstraint constraint]]
   [fschema.error :refer [error error?]])
  (:refer-clojure :exclude [> < <= >= string? number?
                            map? vector? re-matches
                            keyword? symbol? set? integer?
                            seq? coll? list? instance? = not=]))

(def not-nil fschema.core.constraint/not-nil)

(defconstraint any (constantly true))

(defconstraint string? clojure.core/string?)

(defconstraint number? clojure.core/number?)

(defconstraint integer? clojure.core/integer?)

(defconstraint map? clojure.core/map?)

(defconstraint vector? clojure.core/vector?)

(defconstraint seq? clojure.core/seq?)

(defconstraint keyword? clojure.core/keyword?)

(defconstraint symbol? clojure.core/symbol?)

(defconstraint set? clojure.core/set?)

(defconstraint coll? clojure.core/coll?)

(defconstraint list? clojure.core/list?)

(defconstraint instance? [t] (fn [x] (clojure.core/instance? t x)))

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

(defconstraint countable?
  (fn [x]
    (or (clojure.core/instance? clojure.lang.Counted x)
        (clojure.core/instance? clojure.lang.IPersistentCollection x)
        (clojure.core/instance? java.lang.CharSequence x)
        (clojure.core/instance? java.util.Collection x)
        (clojure.core/instance? java.util.Map x)
        (.. x getClass isArray))))

(defconstraint count= [n] (fn [coll] (clojure.core/= (count coll) n))
  :pre-constraint countable?)

(defconstraint count> [n] (fn [coll] (clojure.core/> (count coll) n))
  :pre-constraint countable?)

(defconstraint count< [n] (fn [coll] (clojure.core/< (count coll) n))
  :pre-constraint countable?)

(defconstraint count<= [n] (fn [coll] (clojure.core/<= (count coll) n))
  :pre-constraint countable?)

(defconstraint count>= [n] (fn [coll] (clojure.core/>= (count coll) n))
  :pre-constraint countable?)
