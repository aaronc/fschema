(ns simple-schema.core)

;; from clojure.algo.generic
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

;; error stuff

(defn error [& xs]
  (when-let [errors (seq (remove nil? (flatten xs)))]
    (with-meta (vec errors) {::error true})))

(defn error? [x]  (::error (meta x)))

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
   (fn [x opts]
     (let [res (mutator x opts)]
       (when (error? res) res)))))

(defn validator->mutator [validator]
  (tag-mutator
    (fn [x opts]
      (let [res (validator x opts)]
        (if (error? res)
          res
          x)))))

(defn- mutator-chain [mutators]
  (if (= 1 (count mutators))
    (first mutators)
    (tag-mutator
     (fn mutator-chain [x opts]
       (let [mutated (reduce (fn [x m] (m x opts)) x mutators)]
         (or
          (some error? mutated)
          mutated))))))

(defn map->mutator [x]
  (let [mmap (into {} (map (fn [[k v]] [k (->mutator v)]) x))]
    (tag-mutator
     (fn map-mutator [obj opts]
       (when obj
         (if (map? obj)
           (loop [obj obj [[k m] & more] (seq mmap)]
             (if m
               (if-let [v (if (nil? k) obj (get obj k))]
                 (let [v (m v opts)]
                   (if (error? v)
                     v
                     (recur (assoc obj k v) more)))
                 (recur obj more))
               obj))
           (error {:error-id ::invalid-map :message "Not a valid map"})))))))

(defn ->mutator [& args]
  (mutator-chain
   (for [x args]
     (cond
      (is-mutator? x) x
      (is-validator? x) (validator->mutator x)
      (fn? x) (tag-mutator (fn mutator-wrapper [val opts] (x val)))
      (map? x) (map->mutator x)
      (seq x) (apply ->mutator x)
      :else (throw (ex-info (str "Don't know how to coerce " x " to mutator")
                            {:error-id ::mutator-definition-error
                             :x x :args args}))))))

(defn- validator-chain [validators]
  (if (= 1 (count validators))
    (first validators)
    (tag-validator
     (fn validator-chain [x opts]
       (some identity (map (fn [v] (v x opts)) validators))))))

(defn- prepend-error-paths [errors k]
  (seq
   (if (nil? k)
     errors
     (map
      (fn [err] (update-in err [:path] (fn [path] (conj path k))))
      errors))))

(defn- take-path-step [{:keys [path] :as opts}]
  (if path
    [(first path) (update-in opts [:path] next)]
    [nil opts]))

(defn- map->validator [x]
  (let [vmap (fmap ->validator x)]
    (tag-validator
     (fn map-validator
       [obj opts]
       (when obj
         (if (map? obj)
           (let [[step opts] (take-path-step opts)]
             (error
              (for [[k v] vmap]
                (when (or (not step) (= k step))
                  (let [x (if (nil? k) obj (get obj k))
                        res (v x opts)]
                    (when res
                      (prepend-error-paths res k)))))))
           (error {:error-id ::invalid-map :message "Not a valid map"})))))))

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

(defn simple-validator [{:keys [message name test-fn params]}]
  (tag-validator
   (fn
     [x opts]
     (when x
       (when-not (test-fn x)
         (error {:error-id name :message message :params params :value x}))))))

(def not-nil
  (tag-validator
   (fn required [x opts]
     (when-not x
       (error {:error-id :not-nil :message "Required value missing or nil" :value x})))))

(defmacro defvalidator [vname & kvs]
  (let [vcode (keyword vname)]
    (if (vector? (first kvs))
      (let [args (first kvs)
            test-fn (second kvs)
            kvs (rest (rest kvs))]
        `(defn ~vname ~args
           (simple-schema.core/simple-validator
            (merge {:name ~vcode :params ~args :test-fn ~test-fn}
                   ~(apply hash-map kvs)))))
      `(def ~vname 
         (simple-schema.core/simple-validator
          (merge {:name ~vcode :test-fn ~(first kvs)}
                 ~(apply hash-map (rest kvs))))))))

(defmacro defschema [name & kvs]
  `(def ~name (simple-schema.core/->validator
               simple-schema.core/not-nil
               ~(apply hash-map kvs))))

(defn veach [f]
  (let [f (->validator f)]
    (tag-validator
     (fn veach [xs opts]
       (let [[step opts] (take-path-step opts)]
         (error
          (map-indexed
           (fn [idx x]
             (when (or (not step) (= step idx))
               (when-let [err (f x opts)]
                 (prepend-error-paths err idx))))
           xs)))))))

(defn meach [f]
  (let [f (->mutator)]
    (tag-mutator
     (fn meach [xs opts]
       (let [xs-res
             (map-indexed
              (fn [idx x]
                (let [res (f x opts)]
                  (if (error? res)
                    (prepend-error-paths res idx)
                    res)))
              xs)]
         (if-let [errors (seq (filter error? xs-res))]
           (error errors)
           (vec xs-res)))))))

;; TODO is the wrappers below necessary?

(defn- validate-or-mutate
  [func x & {:keys [throw?] :as opts}]
  (if throw?
    (let [res (func x (dissoc opts :throw?))]
      (when (error? res)
        (throw (ex-info "Validation error" {:error-id ::validation-error
                                            :errors res})))
      res)
    (func x opts)))

(def validate validate-or-mutate)

(def mutate validate-or-mutate)

;; validator-> or ->validator
;; veach or v-each or each-validator or validate-each
;; meach or m-each
