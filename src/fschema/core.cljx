(ns fschema.core)

;; Taken from clojure.algo.generic
(defmulti fmap
  "Applies function f to each item in the data structure s and returns
   a structure of the same kind."
   {:arglists '([f s])}
   (fn [f s] (type s)))

(defmethod fmap clojure.lang.IPersistentList
  [f v]
  (map f v))

(defmethod fmap clojure.lang.IPersistentVector
  [f v]
  (into (empty v) (map f v)))

(defmethod fmap clojure.lang.IPersistentMap
  [f m]
  (into (empty m) (for [[k v] m] [k (f v)])))

(defmethod fmap clojure.lang.IPersistentSet
  [f s]
  (into (empty s) (map f s)))

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

;; errors stuff

(defn- prepend-error-paths [errors k]
  (seq
   (if (nil? k)
     errors
     (map
      (fn [err] (update-in err [:path] (fn [path] (conj path k))))
      errors))))

(defn errors [& xs]
  (when-let [errors (seq (remove nil? (flatten xs)))]
    (with-meta (vec errors) {::errors true})))

(defn errors? [x]  (when (::errors (meta x)) x))

(defn tag-validator [x]
  (with-meta
    x
    {::validator true}))

(defn is-validator? [x] (::validator (meta x)))

(defn tag-mutator [x] (with-meta x {::mutator true}))

(defn is-mutator? [x] (::mutator (meta x)))

(declare ->mutator)

(declare ->validator)

(defn mutator->validator [mutator]
  (tag-validator
   (fn [x]
     (let [res (mutator x)]
       (when (errors? res) res)))))

(defn validator->mutator [validator]
  (tag-mutator
    (fn [x]
      (let [res (validator x)]
        (if (errors? res)
          res
          x)))))

(defn- if-single-first-or [coll f]
  (if (= 1 (count coll)) (first coll) (f)))

(defn- mutator-chain [mutators]
  (if-single-first-or
   mutators
   (fn [] (tag-mutator
           (fn mutator-chain [x]
             (reduce (fn [x m] (if (errors? x) x (m x))) x mutators))))))

(defn get* [obj k] (if (nil? k) obj (get obj k)))

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
                 (errors
                  (map (fn [[k v]]
                         (when (errors? v)
                           (prepend-error-paths v k)))
                       mutations))]
             (or errs
                 (reduce (fn [obj [k v]] (assoc obj k v)) obj mutations)))
           (errors {:error-id ::invalid-map :message "Not a valid map"})))))))

(defn ->mutator [& args]
  (mutator-chain
   (for [x args]
     (cond
      (is-mutator? x) x
      (is-validator? x) (validator->mutator x)
      (fn? x) (tag-mutator (fn [v] (when-not (nil? v) (x v))))
      (map? x) (map->mutator x)
      (seq x) (apply ->mutator x)
      :else (throw (ex-info (str "Don't know how to coerce " x " to mutator")
                            {:error-id ::mutator-definition-error
                             :x x :args args}))))))

(defn- validator-chain [validators]
  (if (= 1 (count validators))
    (first validators)
    (tag-validator
     (fn validator-chain [x]
       (or
        (first (drop-while (comp not errors?) (map (fn [v] (v x)) validators)))
        x)))))

(defn- take-path-step [{:keys [path] :as opts}]
  (if path
    [(first path) (update-in opts [:path] next)]
    [nil opts]))

(defn- map->validator [x]
  (let [vmap (fmap ->validator x)]
    (tag-validator
     (fn map-validator
       [obj]
       (when obj
         (if (map? obj)
           (or
            (errors
             (for [[k v] vmap]
               (when-let [errs (errors? (v (get* obj k)))] 
                 (prepend-error-paths errs k))))
            obj)
           (errors {:error-id ::invalid-map :message "Not a valid map"})))))))

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

(defn constraint* [{:keys [name test-fn message params]}]
  (tag-validator
   (fn test-constraint
     [x]
     (when-not (nil? x)
       (if-not (test-fn x)
         (errors {:error-id (keyword name) :message message :params params :value x})
         x)))))

(defn constraint [name test-fn & {:as opts}]
   (constraint* (merge {:name name :test-fn test-fn} opts)))

(def not-nil
  (tag-validator
   (fn not-nil [x]
     (if-not x
       (errors {:error-id :not-nil :message "Required value missing or nil" :value x})
       x))))

(defmacro defconstraint [vname & kvs]
  (let [vcode (keyword vname)]
    (if (vector? (first kvs))
      (let [args (first kvs)
            test-fn (second kvs)
            kvs (rest (rest kvs))]
        `(defn ~vname ~args
           (simple-schema.core/constraint*
            (merge {:name ~vcode :params ~args :test-fn ~test-fn}
                   ~(apply hash-map kvs)))))
      `(def ~vname 
         (simple-schema.core/constraint*
          (merge {:name ~vcode :test-fn ~(first kvs)}
                 ~(apply hash-map (rest kvs))))))))

(defmacro defschema [name & kvs]
  `(def ~name (simple-schema.core/->validator
               simple-schema.core/not-nil
               ~(apply hash-map kvs))))

(defn validate-each-with [& fs]
  (let [f (apply ->validator fs)]
    (tag-validator
     (fn validate-each [xs]
       (errors
        (map-indexed
         (fn [idx x]
           (when-let [err (errors? (f x))]
             (prepend-error-paths err idx)))
         xs))))))

(defn mutate-each-with [& fs]
  (let [f (apply ->mutator fs)]
    (tag-mutator
     (fn mutate-each [xs]
       (let [xs-res
             (map-indexed
              (fn [idx x]
                (let [res (f x)]
                  (if (errors? res)
                    (prepend-error-paths res idx)
                    res)))
              xs)]
         (if-let [errs (seq (filter errors? xs-res))]
           (errors errs)
           (vec xs-res)))))))
