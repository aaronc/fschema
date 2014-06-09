(ns fschema.core
  (:require
   [fschema.error :refer :all]
   [fschema.util.functor :refer [fmap]]))

;; The core function in fschema is the simple error? function which
;; determines if a given value represents an error object. An error object
;; is any object that has the :error key set in its meta map. Errors should
;; generally be created with the error function which will aggregate multiple
;; error maps into a vector of error maps.

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

;; Type hierachy

;; All validators are simply mutators that don't mutate anything
(derive ::validator ::mutator)

(derive ::constraint ::validator)

(derive ::vmap ::validator)

(derive ::mmap ::mutator)

(derive ::vmap ::mmap)

(derive ::vchain ::validator)

(derive ::mchain ::mutator)

(derive ::vchain ::mchain)

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
  (let [mmap (fmap ->mutator x)]
    (tag-mutator
     (fn map-mutator [obj]
       (when obj
         (if (map? obj)
           (let [mutations
                 (doall
                  (remove
                   nil?
                   (map (fn [[k m]]
                          (let [v (get obj k)
                                mv (m v)]
                            (when-not (= v mv)
                              [k mv])))
                        mmap)))
                 errs
                 (error
                  (map (fn [[k v]]
                         (when (error? v)
                           (prepend-error-paths v k)))
                       mutations))]
             (or errs
                 (reduce (fn [obj [k v]] (assoc obj k v)) obj mutations)))
           (error {:error-id ::invalid-map :message "Not a valid map"})))))))

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
       {::vmap vmap}))))

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

(defn- tag-constraint [f attrs]
  (with-meta f (assoc attrs :type ::constraint)))

(defn constraint* [{:keys [name test-fn message params]}]
  (let [attrs {:error-id (keyword name) :message message :params params}]
    (tag-constraint
      (fn test-constraint
        [x]
        (when-not (nil? x)
          (if-not (test-fn x)
            (error (assoc attrs :value x))
            x)))
      attrs)))

(defn constraint
  ([name test-fn]
     (constraint name nil test-fn))
  ([name opts test-fn]
     (constraint* (merge {:name name :test-fn test-fn} opts))))

(def not-nil
  (let [attrs {:error-id ::not-nil
                :message "Required value missing or nil"}]
    (tag-constraint
      (fn not-nil [x]
        (if (nil? x)
          (error (assoc attrs :value nil))
          x))
      attrs)))

(defmacro defconstraint [vname & kvs]
  (let [vcode (keyword (str *ns*) (name vname))]
    (if (vector? (first kvs))
      (let [args (first kvs)
            test-fn (second kvs)
            kvs (rest (rest kvs))
            attrs (apply hash-map kvs)]
        `(def ~vname
           (with-meta
             (fn ~vname ~args
               (fschema.core/constraint*
                (merge {:name ~vcode :params ~args :test-fn ~test-fn}
                       ~attrs)))
             ~(merge
               {:type ::constraint-constructor
                :name vcode}
               attrs))))
      `(def ~vname 
         (fschema.core/constraint*
          (merge {:name ~vcode :test-fn ~(first kvs)}
                 ~(apply hash-map (rest kvs))))))))

(defmacro defschema [name & kvs]
  `(def ~name (fschema.core/->validator
               fschema.core/not-nil
               ~(apply hash-map kvs))))

(defn veach [& fs]
  (let [f (apply ->validator fs)]
    (tag-validator
     (fn validate-each [xs]
       (error
        (map-indexed
         (fn [idx x]
           (when-let [err (error? (f x))]
             (prepend-error-paths err idx)))
         xs))))))

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
           (vec xs-res)))))))

(defmulti vget-in (fn [v ks] (type v)))

(defconstraint any (constantly true))

(defmethod vget-in :default [v ks]
  (if (and v (not ks))
    v
    any))

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
      {:type ::vmap
       :validator-map vmap})))

(def map->validator vmap)

(defmethod vget-in ::vmap [v ks]
  (let [{:keys [validator-map]} (meta v)]
    (vget-in (get validator-map (first ks)) (next ks))))

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

