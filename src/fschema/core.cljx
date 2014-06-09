(ns fschema.core
  (:use [fschema.util.functor :only [fmap]]))

;; The core function in fschema is the simple error? function which
;; determines if a given value represents an error object. An error object
;; is any object that has the :error key set in its meta map. Errors should
;; generally be created with the error function which will aggregate multiple
;; error maps into a vector of error maps.


(defn error?
  "Determines whether x represents an error. Any object with the :error key set
   in its meta map is considered to be an error. Returns nil if the object does
   not represent an error or the error object that was passed to it."
  [x]
  (when (:error (meta x)) x))

(defn error [& error*]
  "Marks the given map, vector or sequence of maps and vectors as a vector
  containing 1 or more error objects.

  Ex:
    user=> (error {:error-id :test1})
    [{:error-id :test1}] ;; with {:error true} as the vector's meta map

    user=> (error [{:error-id :test1}])
    [{:error-id :test1}] 

    user=> (error [{:error-id :test1}] {:error-id :test2})
    [{:error-id :test1} {:error-id :test2}] 

    user=> (error [{:error-id :test1}] {:error-id :test2} [{:error-id :test3}])
    [{:error-id :test1} {:error-id :test2} {:error-id :test3}]
"
  (when-let [errors (seq (remove nil? (flatten error*)))]
    (with-meta (vec errors) {:error true})))

(defn- prepend-error-paths
  "Given a seq of error maps, prepend the given path to any path vector in
   each error map (will create the path vector with the provided path if none
   exists.

   Ex:
     user=> (prepend-error-paths [{:error-id :test :path [:a]} {:error-id :test2}] :b)
     [{:path [:a :b], :error-id :test} {:path (:b), :error-id :test2}]"
  [errors path]
  (if (nil? path)
    errors
    (error
     (map
      (fn [err] (update-in err [:path] (fn [p] (vec (conj p path)))))
      errors))))


;; (defmulti deep-fmap
;;   "Applies function f to each item in the data structure s and returns
;;    a structure of the same kind."
;;    {:arglists '([f s])}
;;    (fn [f s] (type s)))

;; (defmethod deep-fmap :default [f v] (f v))

;; (defmethod deep-fmap clojure.lang.IPersistentList
;;   [f v]
;;   (map (partial deep-fmap f) v))

;; (defmethod deep-fmap clojure.lang.IPersistentVector
;;   [f v]
;;   (into (empty v) (map (partial deep-fmap f) v)))

;; (defmethod deep-fmap clojure.lang.IPersistentMap
;;   [f m]
;;   (into (empty m) (for [[k v] m] [k (deep-fmap f v)])))

;; (defmethod deep-fmap clojure.lang.IPersistentSet
;;   [f s]
;;   (into (empty s) (map (partial deep-fmap f) s)))

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
       (if (error? res)
         res
         x)))))

(defn- if-single-first-or [coll f]
  (if (= 1 (count coll)) (first coll) (f)))

(defn- mutator-chain [mutators]
  (if (= 1 (count mutators))
    (first mutators)
    (tag-mutator
     (fn mutator-chain [x]
       (reduce (fn [x m] (if (error? x) x (m x))) x mutators)))))

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
    (tag-validator
     (fn validator-chain [x]
       (or
        (first (drop-while (comp not error?) (map (fn [v] (v x)) validators)))
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
            (error
             (for [[k v] vmap]
               (when-let [errs (error? (v (get* obj k)))] 
                 (prepend-error-paths errs k))))
            obj)
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

(defn constraint* [{:keys [name test-fn message params]}]
  (tag-validator
   (fn test-constraint
     [x]
     (when-not (nil? x)
       (if-not (test-fn x)
         (error {:error-id (keyword name) :message message :params params :value x})
         x)))))

(defn constraint
  ([name test-fn]
     (constraint name nil test-fn))
  ([name opts test-fn]
     (constraint* (merge {:name name :test-fn test-fn} opts))))

(def not-nil
  (tag-validator
   (fn not-nil [x]
     (if-not x
       (error {:error-id :not-nil :message "Required value missing or nil" :value x})
       x))))

(defmacro defconstraint [vname & kvs]
  (let [vcode (keyword (str *ns*) (name vname))]
    (if (vector? (first kvs))
      (let [args (first kvs)
            test-fn (second kvs)
            kvs (rest (rest kvs))]
        `(defn ~vname ~args
           (fschema.core/constraint*
            (merge {:name ~vcode :params ~args :test-fn ~test-fn}
                   ~(apply hash-map kvs)))))
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


