(ns onyx-cqrs-example.scenario.factory
  (:require [onyx-cqrs-example.scenario.simple-accounts :as simple-accounts]
            [onyx-cqrs-example.scenario.order-sensitive :as order-sensitive]))


(defn create
  [scenario]
  (case scenario
    :simple-accounts
    (simple-accounts/init)

    :order-sensitive
    (order-sensitive/init)

    ;; else
    {}))
