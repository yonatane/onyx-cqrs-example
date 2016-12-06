(ns onyx-cqrs-example.job
  (:require [onyx-cqrs-example.account-aggregation]))


(defn ^:export dump-aggregate [task-event window trigger state-event state]
  (println (str "account '" (:group state-event) "':" state)))


(defn create []
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
   []

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
