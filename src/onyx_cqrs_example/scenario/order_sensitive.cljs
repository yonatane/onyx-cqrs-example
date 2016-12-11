(ns onyx-cqrs-example.scenario.order-sensitive
  (:require [cljs.pprint :refer [pprint]]
            [onyx-cqrs-example.onyx :as onyx-setup]
            [onyx-cqrs-example.aggregation.order-sensitive :as os-agg]))


(def commands
  [;; account must be created before any other commands
   {:command/id (random-uuid)
    :command/offset 1
    :command/type :command.type/deposit
    :account/id "betty"
    :deposit/amount 100}
   {:command/id (random-uuid)
    :command/offset 0
    :command/type :command.type/create-account
    :account/id "betty"}

   ;; withdraw assuming you already deposited first
   {:command/id (random-uuid)
    :command/offset 0
    :command/type :command.type/create-account
    :account/id "sue"}
   {:command/id (random-uuid)
    :command/offset 2
    :command/type :command.type/withdraw
    :account/id "sue"
    :withdraw/amount 50}
   {:command/id (random-uuid)
    :command/offset 1
    :command/type :command.type/deposit
    :account/id "sue"
    :deposit/amount 100}
   ])


(defmulti process-command (fn [state command] (:command/type command)))

(defmethod process-command :command.type/create-account
  [state command]
  {:event/type :event.type/account-created})

(defmethod process-command :command.type/deposit
  [state command]
  (cond
    (not (:balance state))
    {:event/type :event.type/invalid-command
     :invalid-command/reason "Uninitialized account"}

    ;; All fine
    :else
    {:event/type :event.type/funds-modified
     :funds.modify/amount (:deposit/amount command)}))

(defmethod process-command :command.type/withdraw
  [state command]
  (cond
    (not (:balance state))
    {:event/type :event.type/invalid-command
     :invalid-command/reason "Uninitialized account"}

    (< (:balance state) (:withdraw/amount command))
    {:event/type :event.type/invalid-command
     :invalid-command/reason "Insufficient funds"}

    ;; All fine
    :else
    {:event/type :event.type/funds-modified
     :funds.modify/amount (- (:withdraw/amount command))}))

(defmethod process-command :default
  [state command]
  {:event/type :event.type/unknown-command
   :event/command command})


(defn init-aggregation
  [window]
  {})

(defn create-state-update
  [window state command]
  (-> (process-command state command)
      (assoc :event/id (:command/id command)
             :account/id (:account/id command))))

(defn apply-state-update
  [window state change]
  (case (:event/type change)
    :event.type/account-created
    (assoc state :balance 0)

    :event.type/funds-modified
    (update state :balance + (:funds.modify/amount change))

    ;; else
    state))

(def ^:export aggregation (os-agg/wrap-aggregation init-aggregation create-state-update apply-state-update))

(def ^:export refinement os-agg/refinement)

(def ^:export sync os-agg/sync)


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
