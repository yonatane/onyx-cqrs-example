(ns onyx-cqrs-example.store
  (:require [onyx-cqrs-example.utils :refer [assoc-or]]))


(defn create
  []
  {})

(defn put
  [store id obj]
  (assoc-or store id obj))
