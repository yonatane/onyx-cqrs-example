(ns onyx-cqrs-example.onyx
  (:require [onyx-cqrs-example.account :as account]
            [onyx-cqrs-example.global-reconciler :as global-reconciler]
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

(defn send [onyx-env segments]
  (reduce
    (fn [onyx-env segment]
      (onyx/new-segment onyx-env :in segment))
    onyx-env
    segments))
