(ns onyx-cqrs-example.account
  (:require [cljs.pprint :refer [pprint]]
            [om.next :as om]
            [onyx-cqrs-example.aggregation.event-sourcing :as es-agg]
            [onyx-cqrs-example.global-reconciler :as global-reconciler]))


(defn init
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
     :funds.modify/amount (:deposit/amount command)}

    :command.type/withdraw
    {:event/type :event.type/funds-modified
     :event/id (:command/id command)
     :funds.modify/amount (- (:withdraw/amount command))}

    ;; else
    {:event/type :event.type/unknown-command
     :event/id (:command/id command)}))

(defn apply-state-update
  [window state change]
  (case (:event/type change)
    :event.type/account-created
    (assoc state :balance 0)

    :event.type/funds-modified
    (update state :balance + (:funds.modify/amount change))

    ;; else
    state))


(def ^:export aggregation (es-agg/wrap-aggregation init create-state-update apply-state-update))

(def ^:export refinement es-agg/refinement)

(def ^:export sync es-agg/sync)
