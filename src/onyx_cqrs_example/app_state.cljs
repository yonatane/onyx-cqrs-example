(ns onyx-cqrs-example.app-state
  (:require [onyx-cqrs-example.store :as store]
            [onyx-cqrs-example.onyx :as onyx-setup]))


(def commands
  [{:command/type :command.type/create-account
    :account/id "betty"}
   {:command/type :command.type/create-account
    :account/id "sue"}
   {:command/type :command.type/deposit
    :account/id "betty"
    :deposit/amount 100}
   {:command/type :command.type/deposit
    :account/id "sue"
    :deposit/amount 200}
   {:command/type :command.type/withdraw
    :account/id "betty"
    :withdraw/amount 40}
   {:command/type :command.type/withdraw
    :account/id "sue"
    :withdraw/amount 70}])


(defn create
  []
  (atom
    (list
      {:cqrs.scenario/commands
       commands

       :cqrs/command-store
       (store/create)

       :cqrs/event-store
       (store/create)

       :cqrs.onyx/env
       (onyx-setup/new-onyx-env)})))
