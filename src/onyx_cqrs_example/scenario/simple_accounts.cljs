(ns onyx-cqrs-example.scenario.simple-accounts
  (:require [onyx-cqrs-example.onyx :as onyx-setup]
            [onyx-cqrs-example.aggregation.event-sourcing :as es-agg]))


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


(defn init-aggregation
  [window]
  {})

(defn create-state-update
  [window state command]
  (case (:command/type command)
    :command.type/create-account
    {:event/type :event.type/account-created
     :event/id (:command/id command)
     :account/id (:account/id command)}

    :command.type/deposit
    {:event/type :event.type/funds-modified
     :event/id (:command/id command)
     :account/id (:account/id command)
     :funds.modify/amount (:deposit/amount command)}

    :command.type/withdraw
    {:event/type :event.type/funds-modified
     :event/id (:command/id command)
     :account/id (:account/id command)
     :funds.modify/amount (- (:withdraw/amount command))}

    ;; else
    {:event/type :event.type/unknown-command
     :event/id (:command/id command)
     :account/id (:account/id command)
     :event/command command}))

(defn apply-state-update
  [window state change]
  (case (:event/type change)
    :event.type/account-created
    (assoc state :balance 0)

    :event.type/funds-modified
    (update state :balance + (:funds.modify/amount change))

    ;; else
    state))

(def ^:export aggregation (es-agg/wrap-aggregation init-aggregation create-state-update apply-state-update))

(def ^:export refinement es-agg/refinement)

(def ^:export sync es-agg/sync)


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
     :window/aggregation ::aggregation}]

   :triggers
   [{:trigger/window-id ::account-window
     :trigger/on :onyx.triggers/segment
     :trigger/threshold [1 :elements]
     :trigger/refinement ::refinement
     :trigger/sync ::sync}]})


(defn init
  []
  {:cqrs.scenario/commands commands
   :cqrs.onyx/env (onyx-setup/new-onyx-env job)})
