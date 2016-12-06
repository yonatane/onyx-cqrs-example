(ns onyx-cqrs-example.utils)


; Copied from flatland lib
(defn assoc-or
  "Create mapping from each key to val in map only if existing val is nil."
  ([m key val]
   (if (nil? (m key))
     (assoc m key val)
     m))
  ([m key val & kvs]
   (let [m (assoc-or m key val)]
     (if kvs
       (recur m (first kvs) (second kvs) (nnext kvs))
       m))))
