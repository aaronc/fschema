(ns fschema.core.error)

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

(defn prepend-error-paths
  "Given a seq of error maps, prepend the given path to any path vector in
   each error map (will create the path vector with the provided path if none
   exists.

   Ex:
     user=> (prepend-error-paths [{:error-id :test :path [:a]} {:error-id :test2}] :b)
     [{:path [:b :a], :error-id :test} {:path [:b], :error-id :test2}]"
  [errors path]
  (if (nil? path)
    errors
    (error
     (map
      (fn [err] (update-in err [:path] (fn [p] (vec (cons path p)))))
      errors))))
