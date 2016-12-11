(ns onyx-cqrs-example.scenario.simple-accounts
  (:require [onyx-cqrs-example.account :as account]
            [onyx-cqrs-example.onyx :as onyx-setup]))


(def commands
  [{:command/id 1
    :command/type :command.type/create-account
    :account/id "betty"}
   {:command/id 2
    :command/type :command.type/create-account
    :account/id "sue"}
   {:command/id 3
    :command/type :command.type/deposit
    :account/id "betty"
    :deposit/amount 100}
   {:command/id 4
    :command/type :command.type/deposit
    :account/id "sue"
    :deposit/amount 200}
   {:command/id 5
    :command/type :command.type/withdraw
    :account/id "betty"
    :withdraw/amount 40}
   {:command/id 6
    :command/type :command.type/withdraw
    :account/id "sue"
    :withdraw/amount 70}])


(def job
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
     :lifecycle/calls ::onyx-setup/inject-reconciler-lifecycle-calls}]

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
     :trigger/sync ::account/sync}]})


(defn init
  []
  {:cqrs.scenario/commands commands
   :cqrs.onyx/env (onyx-setup/new-onyx-env job)})
