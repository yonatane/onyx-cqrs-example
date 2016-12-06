(ns onyx-cqrs-example.app-state
  (:require [onyx-cqrs-example.store :as store]
            [onyx-cqrs-example.onyx :as onyx-setup]
            [onyx-local-rt.api :as onyx]
            [om.next :as om]))


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


(defn read
  [env key params]
  (let [state-stack @(:state env)
        latest-state (first state-stack)]
    {:value (get latest-state key)}))


(defmulti mutate om/dispatch)

(defmethod mutate 'cqrs.onyx.env/send-commands
  [env key params]
  {:action
   (fn []
     (swap! (:state env)
            (fn [state-stack]
              (let [latest-state (first state-stack)
                    commands (:commands params)
                    next-state (update-in
                                 latest-state
                                 [:cqrs.onyx/env]
                                 onyx-setup/send commands)]
                (conj state-stack next-state)))))})

(defmethod mutate 'cqrs.state/reset
  [env key params]
  {:action
   (fn []
     (swap! (:state env)
            (fn [state-stack]
              (list (last state-stack)))))})

(defmethod mutate 'cqrs.state/revert
  [env key params]
  {:action
   (fn []
     (swap! (:state env)
            (fn [state-stack]
              (if-let [prev-states (next state-stack)]
                prev-states
                state-stack))))})

(defmethod mutate :default
  [env key params]
  (let [onyx-fn ({'cqrs.onyx.env/tick onyx/tick
                  'cqrs.onyx.env/drain onyx/drain
                  'cqrs.onyx.env/stop onyx/stop} key)]
    {:action
     (fn []
       (swap! (:state env)
              (fn [state-stack]
                (let [latest-state (first state-stack)
                      next-state (update-in
                                   latest-state
                                   [:cqrs.onyx/env]
                                   onyx-fn)]
                  (conj state-stack next-state)))))}))


(defn reconciler
  []
  (om/reconciler
    {:state (create)
     :parser (om/parser {:read read :mutate mutate})}))
