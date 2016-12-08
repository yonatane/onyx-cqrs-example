(ns onyx-cqrs-example.onyx
  (:require [onyx-cqrs-example.account :as account]
            [onyx-cqrs-example.global-reconciler :as global-reconciler]
            [onyx-local-rt.api :as onyx]))


(defn inject-state-before-task-start [event lifecycle]
  (assoc
    event
    :cqrs.app-state/reconciler (global-reconciler/get)))

(def ^:export inject-state-lifecycle-calls
  {:lifecycle/before-task-start inject-state-before-task-start})


(defn job []
  {:workflow
   [[:in ::aggregate-task]
    [::aggregate-task :out]]

   :catalog
   [{:onyx/name :in
     :onyx/type :input
     :onyx/batch-size 1}

    {:onyx/name ::aggregate-task
     :onyx/fn :cljs.core/identity
     :onyx/type :function
     :onyx/group-by-key :account/id
     :onyx/uniqueness-key :command/id  ;; ignored in onyx-local-rt but useful in real onyx.
     :onyx/min-peers 3
     :onyx/flux-policy :recover
     :onyx/batch-size 1}

    {:onyx/name :out
     :onyx/type :output
     :onyx/batch-size 1}]

   :lifecycles
   [{:lifecycle/task ::aggregate-task
     :lifecycle/calls ::inject-state-lifecycle-calls}]

   :windows
   [{:window/id ::account-window
     :window/task ::aggregate-task
     :window/type :global
     :window/aggregation ::account/aggregation}]

   :triggers
   [{:trigger/window-id ::account-window
     :trigger/on :onyx.triggers/segment
     :trigger/threshold [1 :elements]
     :trigger/refinement ::account/refinement
     :trigger/sync ::account/sync}]
   })


(defn new-onyx-env
  ([]
   (new-onyx-env (job)))
  ([job]
   (onyx/init job)))

(defn send [onyx-env segments]
  (reduce
    (fn [onyx-env segment]
      (onyx/new-segment onyx-env :in segment))
    onyx-env
    segments))
