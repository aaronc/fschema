(ns simple-schema.core)

(defn error [& xs]
  (when-let [errors (seq (flatten (remove nil? xs)))]
    (with-meta (vec errors) {::error true})))

(defn error? [x]  (::error (meta x)))

(defn tag-validator [x] (with-meta x {::validator true}))

(defn is-validator? [x] (::validator (meta x)))

(defn tag-mutator [x] (with-meta x {::validator true}))

(defn is-mutator? [x] (::mutator (meta x)))

(defn mutator->validator [mutator]
  (tag-validator
   (fn [x & opts]
     (let [res (apply mutator x opts)]
       (when (error? res) res)))))

(defn validator->mutator [validator]
  (tag-mutator
    (fn [x & opts]
      (let [res (apply validator x opts)]
        (when (error? x)
          x)))))

(defn- mutator-chain [mutators]
  (if (= 1 (count mutators))
    (first mutators)
    (tag-mutator
     (fn mutator-chain [x & opts]
       (loop [x x [m & more] mutators]
         (if m
           (let [x (apply m x opts)]
             (if (error? x)
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
                   (if (error? v)
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

(defn- take-path-step [{:keys [path] :as opts}]
  (if path
    [(first path) (update-in opts [:path] next)]
    [nil opts]))

(defn- map->validator [x]
  (let [vmap (into {} (map (fn [[k v]] [k (->validator v)]) x))]
    (tag-validator
     (fn [obj & opts]
       (when obj
         (if (map? obj)
           (let [[step opts] (take-path-step opts)]
             (apply
              error
              (for [[k v] vmap]
                (when (or (not step) (= k step))
                  (let [x (get obj k)
                        res (apply v x opts)]
                    (prepend-error-paths res k))))))
           (error {:code ::invalid-map :message "Not a valid map"})))))))

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
                             :x x :args args}))))))

(defn simple-validator [{:keys [message name test-fn params]}]
  (tag-validator
   (fn
     [x & opts]
     (when x
       (when-not (test-fn x)
         (error {:code name :message message :params params :value x}))))))

(def not-nil
  (tag-validator
   (fn required [x & opts]
     (when-not x
       (error {:code ::required :message "Required value missing or nil" :value x})))))

(defmacro defvalidator [vname & kvs]
  (let [vcode (keyword (str *ns* "/" vname))]
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
  `(def ~name (simple-schema.core/->validator ~(apply hash-map kvs))))

(defn veach [f]
  (tag-validator
   (fn veach [xs & {:as opts}]
     (let [[step opts] (take-path-step opts)]
       (println step)
       (error
        (map-indexed
         (fn [idx x]
           (when (or (not step) (= step idx))
             (when-let [err (apply f x opts)]
               (prepend-error-paths err idx))))
         xs))))))

(defn meach [f]
  (tag-mutator
   (fn meach [xs & opts]
     (let [xs-res
           (map-indexed
            (fn [idx x]
              (let [res (apply f x opts)]
                (if (error? res)
                  (prepend-error-paths res idx)
                  res)))
            xs)]
       (if-let [errors (seq (filter error? xs-res))]
         (error errors)
         (vec xs-res))))))

