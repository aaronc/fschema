(ns fschema.core.constraint
  (:require
   [fschema.error :refer :all]))

(defn tag-constraint [f attrs]
  (with-meta f (assoc attrs :type :fschema.core/constraint)))

(defn constraint* [{:keys [name test-fn pre-constraint] :as attrs}]
  (let [attrs
        (-> attrs
            (dissoc :name :test-fn :pre-constraint)
            (merge {:error-id (keyword name)}))

        cstrnt
        (tag-constraint
         (fn test-constraint [x]
           (when-not (nil? x)
             (if-not (test-fn x)
               (error (assoc attrs :value x))
               x)))
         attrs)]
    (if pre-constraint
      (tag-constraint
       (fn test-pre-constraint [x]
         (if-let [err (error? (pre-constraint x))]
           err
           (cstrnt x)))
       attrs)
      cstrnt)))

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

(def not-nil
  (let [attrs {:error-id :fschema.constraints/not-nil
                ;:message "Required value missing or nil"
               }]
    (tag-constraint
      (fn not-nil [x]
        (if (nil? x)
          (error (assoc attrs :value nil))
          x))
      attrs)))
