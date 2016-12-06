(ns onyx-cqrs-example.onyx
  (:require [onyx-cqrs-example.account-aggregation]
            [onyx-local-rt.api :as onyx]))


;(defn ^:export before-task-start [event lifecycle]
;  event) ;TODO: init event/command store or get connection to it.
;
;(def ^:export logging-lifecycle-calls
;  {:lifecycle/before-task-start before-task-start})


(defn ^:export dump-aggregate [task-event window trigger state-event state]
  (println (str "account '" (:group state-event) "': " state)))


(defn job []
  {:workflow
   [[:in :task.id/account]
    [:task.id/account :out]]

   :catalog
   [{:onyx/name :in
     :onyx/type :input
     :onyx/batch-size 1}
    {:onyx/name :task.id/account
     :onyx/fn :cljs.core/identity
     :onyx/type :function
     :onyx/group-by-key :account/id
     :onyx/min-peers 3
     :onyx/flux-policy :recover
     :onyx/batch-size 1}
    {:onyx/name :out
     :onyx/type :output
     :onyx/batch-size 1}]

   :lifecycles
   [
    ;{:lifecycle/task :task.id/account
    ; :lifecycle/calls ::logging-lifecycle-calls}
    ]

   :windows
   [{:window/id :window.id/account
     :window/task :task.id/account
     :window/type :global
     :window/aggregation :onyx-cqrs-example.account-aggregation/aggregation}]

   :triggers
   [{:trigger/window-id :window.id/account
     :trigger/on :onyx.triggers/segment
     :trigger/threshold [1 :elements]
     :trigger/refinement :onyx.refinements/accumulating
     :trigger/sync ::dump-aggregate}]
   })


(defn new-onyx-env
  ([]
   (new-onyx-env (job)))
  ([job]
   (reduce
     (fn [onyx-env segment]
       (onyx/new-segment onyx-env :in segment))
     (onyx/init job)
     commands)))

(defn send [onyx-env segments]
  (reduce
    (fn [onyx-env segment]
      (onyx/new-segment onyx-env :in segment))
    onyx-env
    segments))
