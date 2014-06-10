(ns fschema.core
  (:require
   [fschema.error :refer :all]
   [fschema.util.functor :refer [fmap]]
   [fschema.constraints :as c]
   [fschema.core.constraint]))

;; Type hierachy

;; All validators are simply mutators that don't mutate anything
(derive ::validator ::mutator)

(derive ::constraint ::validator)

(derive ::map-validator ::validator)

(derive ::map-mutator ::mutator)

(derive ::map-validator ::map-mutator)

(derive ::vchain ::validator)

(derive ::mchain ::mutator)

(derive ::vchain ::mchain)

(derive ::veach ::validator)

(derive ::meach ::mutator)

(defmulti deep-fmap
  "Applies function f to each item in the data structure s and returns
   a structure of the same kind."
   {:arglists '([f s])}
   (fn [f s] (type s)))

(defmethod deep-fmap :default [f v] (f v))

(defmethod deep-fmap clojure.lang.IPersistentList
  [f v]
  (map (partial deep-fmap f) v))

(defmethod deep-fmap clojure.lang.IPersistentVector
  [f v]
  (into (empty v) (map (partial deep-fmap f) v)))

(defmethod deep-fmap clojure.lang.IPersistentMap
  [f m]
  (into (empty m) (for [[k v] m] [k (deep-fmap f v)])))

(defmethod deep-fmap clojure.lang.IPersistentSet
  [f s]
  (into (empty s) (map (partial deep-fmap f) s)))

(defn tag-validator [x]
  (with-meta
    x
    (merge (meta x) {:type ::validator})))

(defn is-validator? [x] (isa? (type x) ::validator))

(defn tag-mutator [x]
  (with-meta
    x
    (merge (meta x) {::mutator true})))

(defn is-mutator? [x] (::mutator (meta x)))

(declare ->mutator)

(declare ->validator)

(defn mutator->validator [mutator]
  (tag-validator
   (fn [x]
     (let [res (mutator x)]
       (if (error? res)
         res
         x)))))

(defn- mutator-chain [mutators]
  (if (= 1 (count mutators))
    (first mutators)
    (tag-mutator
     (fn mutator-chain [x]
       (reduce (fn [x m] (if (error? x) x (m x))) x mutators)))))

(defn map->mutator [x]
  (let [mutator-map (fmap ->mutator x)]
    (with-meta
      (->mutator
       c/map?
       (fn map-mutator [obj]
         (let [mutations
               (doall
                (remove
                 nil?
                 (map (fn [[k m]]
                        (let [v (get obj k)
                              mv (m v)]
                          (when-not (= v mv)
                            [k mv])))
                      mutator-map)))
               errs
               (error
                (map (fn [[k v]]
                       (when (error? v)
                         (prepend-error-paths v k)))
                     mutations))]
           (or errs
               (reduce (fn [obj [k v]] (assoc obj k v)) obj mutations)))))
      {:type ::map-mutator :fn-map mutator-map})))

(defn ->mutator [& args]
  (mutator-chain
   (for [x args]
     (cond
      (is-mutator? x) x
      (is-validator? x) x
      (fn? x) (tag-mutator (fn [v] (when-not (nil? v) (x v))))
      (map? x) (map->mutator x)
      (seq x) (apply ->mutator x)
      :else (throw (ex-info (str "Don't know how to coerce " x " to mutator")
                            {:error-id ::mutator-definition-error
                             :x x :args args}))))))

(defn- validator-chain [validators]
  (if (= 1 (count validators))
    (first validators)
    (with-meta
      (fn validator-chain [x]
        (or
         (first (drop-while (comp not error?) (map (fn [v] (v x)) validators)))
         x))
      {:type ::vchain
       :validators validators})))

(defn- take-path-step [{:keys [path] :as opts}]
  (if path
    [(first path) (update-in opts [:path] next)]
    [nil opts]))

(defn- map->validator [x]
  (let [vmap (fmap ->validator x)]
    (tag-validator
     (with-meta
       (fn map-validator
         [obj]
         (when obj
           (if (map? obj)
             (or
              (error
               (for [[k v] vmap]
                 (when-let [errs (error? (v (get obj k)))] 
                   (prepend-error-paths errs k))))
              obj)
             (error {:error-id ::invalid-map :message "Not a valid map"}))))
       {::map-validator vmap}))))

(defn ->validator [& args]
  (validator-chain
   (for [x args]
     (cond
      (is-validator? x) x
      (is-mutator? x) (mutator->validator x)
      (map? x) (map->validator x)
      (seq x) (apply ->validator x)
      :else (throw (ex-info (str "Don't know how to coerce " x " to validator")
                            {:error-id ::validator-definition-error
                             :x x :args args}))))))

(defmacro defschema [name & kvs]
  `(def ~name (fschema.core/->validator
               fschema.core.constraint/not-nil
               ~(apply hash-map kvs))))

(defn veach [& fs]
  (let [vf (apply ->validator fs)]
    (with-meta
     (fn validate-each [xs]
       (let [xs-res
             (map-indexed
              (fn [idx x]
                (when-let [err (error? (vf x))]
                  (prepend-error-paths err idx)))
              xs)]
         (if-let [errs (seq (filter error? xs-res))]
           (error errs)
           xs)))
     {:type ::veach
      :validator vf})))

(defn meach [& fs]
  (let [f (apply ->mutator fs)]
    (tag-mutator
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
             (vec xs-res))))))))

(defmulti vget-in (fn [v ks] (type v)))


(defmethod vget-in :default [v ks]
  (if (and v (not ks))
    v
    c/any))

(defn vmap [x]
  (let [vmap (fmap ->validator x)]
    (with-meta
      (fn map-validator
        [obj]
        (when obj
          (if (map? obj)
            (or
             (error
              (for [[k v] vmap]
                (when-let [errs (error? (v (get obj k)))] 
                  (prepend-error-paths errs k))))
             obj)
            (error {:error-id ::invalid-map :message "Not a valid map"}))))
      {:type ::map-validator
       :validator-map vmap})))

(def map->validator vmap)

(defmethod vget-in ::map-mutator [v ks]
  (let [{:keys [fn-map]} (meta v)]
    (vget-in (get fn-map (first ks)) (next ks))))

(defmethod vget-in ::vchain [v ks]
  (let [{:keys [validators]} (meta v)]
    (validator-chain (map #(vget-in % ks) validators))))

(defn mwhere [test-fn mutator-fn]
  (let [test-fn (if (is-validator? test-fn)
                  (fn validator-test-fn [x] (not (error? (test-fn x))))
                  test-fn)]
    (fn where-mutator
      [x]
      (if (test-fn x)
        (mutator-fn x)
        x))))

(defn mall [mutator-fn]
  (fn all-mutator
    [x]
    (deep-fmap mutator-fn x)))

(defn mall-where [test-fn mutator-fn]
  (mall (mwhere test-fn mutator-fn)))

