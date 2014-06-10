(ns fschema.core.constraint
  (:require
   [fschema.error :refer :all]))

(defn tag-constraint [f attrs]
  (with-meta f (assoc attrs :type :fschema.core/constraint)))

(defn constraint* [{:keys [name test-fn] :as attrs}]
  (let [attrs
        (-> attrs
            (dissoc :name :test-fn)
            (merge {:error-id (keyword name)}))]
    (tag-constraint
      (fn test-constraint
        [x]
        (when-not (nil? x)
          (if-not (test-fn x)
            (error (assoc attrs :value x))
            x)))
      attrs)))

(defn constraint
  [name test-fn & {:as opts}]
  (constraint* (merge {:name name :test-fn test-fn} opts)))

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
               (fschema.core.constraint/constraint*
                (merge {:name ~vcode :params ~args :test-fn ~test-fn}
                       ~attrs)))
             ~(merge
               {:type ::constraint-constructor
                :name vcode}
               attrs))))
      `(def ~vname 
         (fschema.core.constraint/constraint*
          (merge {:name ~vcode :test-fn ~(first kvs)}
                 ~(apply hash-map (rest kvs))))))))
