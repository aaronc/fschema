(ns simple-schema.core-monadic
  (:use [clojure.algo.monads]))

(defn error [& xs]
  (when-let [errors (seq (remove nil? (flatten xs)))]
    (with-meta (vec errors) {::error true})))

(defn error? [x]  (::error (meta x)))

(defn tag-validator [x]
  (with-meta
    x
    {::validator true}))

(defn is-validator? [x] (::validator (meta x)))

(defn constraint [constraint-name test-fn & {:keys [message params]}]
  (tag-validator
   (fn
     [x]
     (when-not (nil? x)
       (if-not (test-fn x)
         (error {:error-id (keyword constraint-name) :message message :params params :value x})
         x)))))

(def not-nil
  (tag-validator
   (fn not-nil [x]
     (if-not x
       (error {:error-id :not-nil :message "Required value missing or nil" :value x})
       x))))

(defmonad vm-monad
  [m-bind (fn [v f] (if (error? v) v (f v)))
   m-result identity])
