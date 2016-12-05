(ns clojure.future
  "Temporary workaround for clojure-only clojure-future-spec dep."
  (:refer-clojure :exclude [any? boolean? uuid?]))


(defn any? [x]
  true)

(defn boolean? [x]
  (instance? boolean x))

(defn uuid? [x]
  (instance? UUID x))
