(ns fschema.core
  (:require
   [fschema.error :refer :all]
   [fschema.constraints :as c]
   [fschema.core.constraint]))

;; Type hierachy
(derive ::constraint ::fschema-fn)

(derive ::fschema-map ::fschema-fn)

(derive ::fschema-chain ::fschema-fn)

(derive ::each ::fschema-fn)

(derive ::where ::fschema-fn)

(declare schema-fn)

(defn- make-fschema-chain [fns]
  (if (= 1 (count fns))
    (first fns)
    (with-meta
     (fn fschema-chain [x]
       (reduce (fn [x m] (if (error? x) x (m x))) x fns))
     {:type ::fschema-chain
      :fns fns})))

(defn- make-fschema-map [x]
  (let [fn-map (into {} (for [[k v] x] [k (schema-fn v)]))]
    (with-meta
      (schema-fn
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

(defn each [& fs]
  (let [f (apply schema-fn fs)]
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
  (with-meta
    (fn where-fn-wrapper
      [x]
      (let [test (where-fn x)]
        (cond
         (error? test) test
         test (f x)
         :default x)))
    {:type ::where :where-fn where-fn :f f}))

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
    (make-fschema-chain (map #(for-path % ks) validators))))

;; (defmethod for-path ::each [v ks]
;;   (let [{:keys [f]} (meta v)]
;;     (validator-chain (map #(for-path % ks) validators))))

;; Introspection

(defmulti inspect type)

(defmethod inspect :default [f] f)

(defmethod inspect ::fschema-fn [f] (meta f))

(defmethod inspect ::each [f] (update-in (meta f) [:validator] inspect))

(defmethod inspect ::chain-fn [f] (update-in (meta f) [:validators] (partial map inspect)))
