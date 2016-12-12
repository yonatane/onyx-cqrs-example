(ns onyx-cqrs-example.onyx
  (:require [onyx-cqrs-example.global-reconciler :as global-reconciler]
            [onyx-local-rt.api :as onyx]))


(defn inject-reconciler-before-task-start [event lifecycle]
  (assoc
    event
    :cqrs.app-state/reconciler (global-reconciler/get)))

(def ^:export inject-reconciler-lifecycle-calls
  {:lifecycle/before-task-start inject-reconciler-before-task-start})


(defn new-onyx-env
  [job]
  (onyx/init job))

(defn send
  ([onyx-env segments]
   (send onyx-env :in segments))
  ([onyx-env input-task segments]
   (reduce
     (fn [onyx-env segment]
       (onyx/new-segment onyx-env input-task segment))
     onyx-env
     segments)))
