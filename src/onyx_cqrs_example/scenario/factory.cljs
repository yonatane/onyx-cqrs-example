(ns onyx-cqrs-example.scenario.factory
  (:require [onyx-cqrs-example.scenario.simple-accounts :as simple-accounts]))


(defn create
  [scenario]
  (case scenario
    :simple-accounts
    (simple-accounts/init)

    ;; else
    {}))
