(ns onyx-cqrs-example.global-reconciler)


(def reconciler (atom nil))


(defn init [v] (reset! reconciler v))

(defn get [] @reconciler)
