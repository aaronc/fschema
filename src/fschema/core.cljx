(ns fschema.core
  (:require
   [fschema.core.error :refer [prepend-error-paths]]
   [fschema.constraints :as c]
   [fschema.core.constraint]))

(def error fschema.core.error/error)

(def error? fschema.core.error/error?)

;; Type hierachy
(derive ::constraint ::fschema-fn)

(derive ::fschema-map ::fschema-fn)

(derive ::fschema-chain ::fschema-fn)

(derive ::each ::fschema-fn)

(derive ::where ::fschema-fn)

(declare schema-fn)

(defn- make-fschema-chain [fns]
  (let [fns (remove #(#{c/any identity} %) fns)]
    (condp = (count fns)
      0 c/any

      1 (first fns)

      (with-meta
        (fn fschema-chain [x]
          (reduce (fn [x m] (if (error? x) x (m x))) x fns))
        {:type ::fschema-chain
         :fns fns}))))

(defn- make-fschema-map [x]
  (let [fn-map (into {} (for [[k v] x] [k (schema-fn v)]))]
    (with-meta
      (schema-fn
       c/not-nil
       c/map?
       (fn fschema-map [obj]
         (let [results
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
                     results))]
           (or errs
               (reduce (fn [obj [k v]] (assoc obj k v)) obj results)))))
      {:type ::fschema-map :fn-map fn-map})))

(defn schema-fn [& args]
  (make-fschema-chain
   (for [x args]
     (cond
      (fn? x) x
      (map? x) (make-fschema-map x)
      (vector? x) (apply schema-fn x)
      :else (throw (ex-info (str "Don't know how to coerce " x " to an fschema fn")
                            {:error-id ::schema-fn-definition-error
                             :value x :args args}))))))


(defmulti ^:private each-fn (fn [f x] (type x)))

(defn- indexed-each-fn [f xs constructor]
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
      (if (= xs-res xs) xs (constructor xs-res)))))

(defmethod each-fn :default [f xs]
  (error {:error-id :fschema.constraints/eachable?
          :value xs}))

(defmethod each-fn clojure.lang.IPersistentList
  [f xs] (indexed-each-fn f xs identity))

(defmethod each-fn clojure.lang.IPersistentVector
  [f xs] (indexed-each-fn f xs (fn [xs-new] (into (empty xs) xs-new))))

(defmethod each-fn clojure.lang.IPersistentMap
  [f m]
  (let [xs-res
        (map
         (fn [kvp]
           (let [res (f kvp)]
             (if (error? res)
               (prepend-error-paths res (first kvp))
               res)))
         m)]
    (if-let [errs (seq (filter error? xs-res))]
      (error errs)
      (let [res-m (into (empty m) xs-res)]
        (if (= res-m m) m res-m)))))

(defmethod each-fn clojure.lang.IPersistentSet
  [f s]
  (let [xs-res (map f s)]
    (if-let [errs (seq (filter error? xs-res))]
      (error errs)
      (let [res (into (empty s) xs-res)]
        (if (= res s) s res)))))

(defn each [& fs]
  (let [f (apply schema-fn fs)]
    (schema-fn
     c/not-nil
     c/eachable?
     (with-meta
       (fn each-fn-wrapper [xs]
         (each-fn f xs))
       {:type ::each
        :func f}))))

(defn where [test-fn & fs]
  (let [f (apply schema-fn fs)]
    (with-meta
      (fn where-fn
        [x]
        (let [test (test-fn x)]
          (cond
           (error? test) x
           test (f x)
           :default x)))
      {:type ::where :test-fn test-fn :f f})))

(defn optional [& fs]
  (apply where some? fs))

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
  (let [{:keys [fns]} (meta v)]
    (make-fschema-chain (map #(for-path % ks) fns))))

(defmethod for-path ::each [v ks]
  (let [{:keys [func]} (meta v)]
    (for-path func (next ks))))

;; Introspection

(defmulti inspect type)

(defmethod inspect :default [f] f)

(defmethod inspect ::fschema-fn [f] (meta f))

(defmethod inspect ::each [f]
  (update-in (meta f) [:func] inspect))

(defmethod inspect ::fschema-chain [f]
  (update-in (meta f) [:fns] (partial map inspect)))

(defmethod inspect ::fschema-map [f]
  (update-in (meta f) [:fn-map]
             (fn [fn-map] (into {} (map (fn [k v] [k (inspect v)] fn-map))))))
