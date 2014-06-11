(ns fschema.core
  (:require
   [fschema.error :refer :all]
   [fschema.util.functor :refer [fmap]]
   [fschema.constraints :as c]
   [fschema.core.constraint]))

;; Type hierachy
(derive ::constraint ::fschema-fn)

(derive ::fschema-map ::fschema-fn)

(derive ::fschema-chain ::fschema-fn)

(derive ::each ::fschema-fn)

(derive ::where ::fschema-fn)

(derive ::all ::fschema-fn)

(defmulti deep-fschema-fn
  "Applies function f to each item in the data structure s and returns
   a structure of the same kind."
   {:arglists '([f s])}
   (fn [f s] (type s)))

(defmethod deep-fschema-fn :default [f v] (f v))

(defmethod deep-fschema-fn clojure.lang.IPersistentList
  [f v]
  (map (partial deep-fschema-fn f) v))

(defmethod deep-fschema-fn clojure.lang.IPersistentVector
  [f v]
  (into (empty v) (map (partial deep-fschema-fn f) v)))

(defmethod deep-fschema-fn clojure.lang.IPersistentMap
  [f m]
  (into (empty m) (for [[k v] m] [k (deep-fschema-fn f v)])))

(defmethod deep-fschema-fn clojure.lang.IPersistentSet
  [f s]
  (into (empty s) (map (partial deep-fschema-fn f) s)))

(declare fschema)

(defn- make-fschema-chain [fns]
  (if (= 1 (count fns))
    (first fns)
    (with-meta
     (fn fschema-chain [x]
       (reduce (fn [x m] (if (error? x) x (m x))) x fns))
     {:type ::fschema-chain
      :fns fns})))

(defn- make-fschema-map [x]
  (let [fn-map (fmap fschema x)]
    (with-meta
      (fschema
       c/map?
       (fn fschema-map [obj]
         (let [mutations
               (doall
                (remove
                 nil?
                 (map (fn [[k m]]
                        (let [v (get obj k)
                              mv (m v)]
                          (when-not (= v mv)
                            [k mv])))
                      fn-map)))
               errs
               (error
                (map (fn [[k v]]
                       (when (error? v)
                         (prepend-error-paths v k)))
                     mutations))]
           (or errs
               (reduce (fn [obj [k v]] (assoc obj k v)) obj mutations)))))
      {:type ::fschema-map :fn-map fn-map})))

(defn fschema [& args]
  (mutator-chain
   (for [x args]
     (cond
      (fn? x) x
      (map? x) (map->mutator x)
      (seq x) (apply fschema x)
      :else (throw (ex-info (str "Don't know how to coerce " x " to an fschema fn")
                            {:error-id ::mutator-definition-error
                             :x x :args args}))))))

(defn each [& fs]
  (let [f (apply ->mutator fs)]
    (with-meta
     (fn mutate-each [xs]
       (let [xs-res
             (map-indexed
              (fn [idx x]
                (let [res (f x)]
                  (if (error? res)
                    (prepend-error-paths res idx)
                    res)))
              xs)]
         (if-let [errs (seq (filter error? xs-res))]
           (error errs)
           (if (= xs-res xs)
             xs
             (vec xs-res)))))
     {:type ::each
      :func f})))

(defn where [where-fn f]
  (let [where-fn (if (is-validator? where-fn)
                  (fn validator-where-fn [x] (not (error? (where-fn x))))
                  where-fn)]
    (with-meta
      (fn where-mutator
        [x]
        (if (where-fn x)
          (f x)
          x))
      {:type ::where :where-fn where-fn :f f})))

(defn all [mutator-fn]
  (fn all-mutator
    [x]
    (deep-fmap mutator-fn x)))

;; Decomposing Schemas for Property Paths

(defmulti for-path (fn [v ks] (type v)))

(defmethod for-path :default [v ks]
  (if (and v (not ks))
    v
    c/any))

(defmethod for-path ::fschema-map [v ks]
  (let [{:keys [fn-map]} (meta v)]
    (for-path (get fn-map (first ks)) (next ks))))

(defmethod for-path ::fschema-chain [v ks]
  (let [{:keys [validators]} (meta v)]
    (validator-chain (map #(for-path % ks) validators))))

;; (defmethod for-path ::each [v ks]
;;   (let [{:keys [f]} (meta v)]
;;     (validator-chain (map #(for-path % ks) validators))))

;; Introspection

(defmulti inspect type)

(defmethod inspect :default [f] f)

(defmethod inspect ::fschema-fn [f] (meta f))

(defmethod inspect ::each [f] (update-in (meta f) [:validator] inspect))

(defmethod inspect ::chain-fn [f] (update-in (meta f) [:validators] (partial map inspect)))
