(ns simple-schema.core)

(defn error [& xs] (with-meta (vec xs) {::error true}))

(defn is-error? [x]  (::error (meta x)))

(defn tag-validator [x] (with-meta x {::validator true}))

(defn is-validator? [x] (::validator (meta x)))

(defn tag-mutator [x] (with-meta x {::validator true}))

(defn is-mutator? [x] (::mutator (meta x)))

(defn mutator->validator [mutator]
  (tag-validator
   (fn [x & opts]
     (let [res (apply mutator x opts)]
       (when (is-error? res) res)))))

(defn validator->mutator [validator]
  (tag-mutator
    (fn [x & opts]
      (let [res (apply validator x opts)]
        (when (is-error? x)
          x)))))

(defn- mutator-chain [mutators]
  (if (= 1 (count mutators))
    (first mutators)
    (tag-mutator
     (fn mutator-chain [x & opts]
       (loop [x x [m & more] mutators]
         (if m
           (let [x (apply m x opts)]
             (if (is-error? x)
               x
               (recur x more)))
           x))))))

(defn map->mutator [x]
  (let [mmap (into {} (map (fn [[k v]] [k (->mutator v)]) x))]
    (tag-mutator
     (fn map-mutator [obj & opts]
       (when obj
         (if (map? obj)
           (loop [obj obj [[k m] & more] (seq mmap)]
             (if m
               (if-let [v (get obj k)]
                 (let [v (apply m v opts)]
                   (if (is-error? v)
                     v
                     (recur (assoc obj k v) more)))
                 (recur obj more))
               obj))
           (error {:code ::invalid-map :message "Not a valid map"})))))))

(defn ->mutator [& args]
  (mutator-chain
   (for [x args]
     (cond
      (is-mutator? x) x
      (is-validator? x) (validator->mutator x)
      (fn? x) (fn mutator-wrapper [val & opts] (x val))
      (map? x) (map->mutator x)
      (seq x) (apply ->mutator x)
      :else (throw (ex-info (str "Don't know how to coerce " x " to mutator")
                            {:type ::mutator-definition-error
                             :x x :args args}))))))

(defn- validator-chain [validators]
  (if (= 1 (count validators))
    (first validators)
    (tag-validator
     (fn validator-chain [x & opts]
       (loop [[v & more] validators]
         (when v
           (if-let [res (apply v x opts)]
             res
             (recur more))))))))

(defn- prepend-error-paths [errors k]
  (seq
   (map
    (fn [err] (update-in err [:path] (fn [path] (conj path k))))
    errors)))

(defn- map->validator [x]
  (let [vmap (into {} (map (fn [[k v]] [k (->validator v)]) x))]
    (tag-validator
     (fn [obj & opts]
       (when obj
         (if (map? obj)
           (apply
            concat
            (for [[k v] vmap]
              (let [x (get obj k)
                    res (apply v x opts)]
                (prepend-error-paths res k))))
           [{:code ::invalid-map :message "Not a valid map"}])))))

  (defn ->validator [& args]
    (validator-chain
     (for [x args]
       (cond
        (is-validator? x) x
        (is-mutator? x) (mutator->validator x)
        (map? x) (map->validator x)
        (seq x) (apply ->validator x)
        :else (throw (ex-info (str "Don't know how to coerce " x " to validator")
                              {:type ::validator-definition-error
                               :x x :args args})))))))

(defn simple-validator [{:keys [message name test-fn params]}]
  (tag-validator
   (fn
     [x & opts]
     (when x
       (when-not (test-fn x)
         [{:code name :message message :params params :value x}])))))

(def required
  (tag-validator
   (fn required [x & opts]
     (when-not x
       [{:code ::required :message "Required value missing" :value x}]))))

(defmacro defvalidator [vname & kvs]
  (if (vector? (first kvs))
    (let [args (first kvs)
          kvs (rest kvs)]
      `(defn ~vname ~args
         (simple-schema.core/simple-validator
          (merge {:name ~(keyword (str *ns* "/" vname)) :params ~args}
                 ~(apply hash-map kvs)))))
    `(def ~name 
       (simple-schema.core/simple-validator ~(apply hash-map kvs)))))

(defmacro defschema [name & kvs]
  `(def ~name (simple-schema.core/->validator ~(apply hash-map kvs))))

;; (defn validate-each [validator]
;;   (fn validate-each [xs & opts]
;;     (seq
;;      (concat
;;       (for [x xs]
;;         (when-let [err (validator x)]
;;           (prepend-error-paths err )))))
;;      (when-not x
;;        [{:code ::required :message "Required value missing" :value x}])))
