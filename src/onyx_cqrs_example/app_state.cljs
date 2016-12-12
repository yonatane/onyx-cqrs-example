(ns onyx-cqrs-example.app-state
  (:require [cljs.pprint :refer [pprint]]
            [onyx-cqrs-example.scenario.factory :as scenario-factory]
            [onyx-cqrs-example.store :as store]
            [onyx-cqrs-example.onyx :as onyx-setup]
            [onyx-local-rt.api :as onyx]
            [om.next :as om]))


(def default-job
  {:workflow
   [[:in :out]]

   :catalog
   [{:onyx/name :in
     :onyx/type :input
     :onyx/batch-size 1}

    {:onyx/name :out
     :onyx/type :output
     :onyx/batch-size 1}]})

(defn default-state
  []
  {:cqrs.scenario/commands
   []

   :cqrs/command-store
   (store/create)

   :cqrs/event-store
   (store/create)

   :cqrs.onyx/env
   (onyx-setup/new-onyx-env default-job)})


(defn init-state-stack
  []
  (atom
    (list
      (default-state))))


(defn read
  [env key params]
  (let [state-stack @(:state env)
        latest-state (first state-stack)]
    {:value (get latest-state key)}))


(defmulti mutate om/dispatch)

(defmethod mutate 'cqrs.state/init-scenario
  [env key {:keys [scenario]}]
  {:action
   (fn []
     (swap! (:state env)
            (fn [state-stack]
              (-> state-stack
                  (last)
                  (merge (scenario-factory/create scenario))
                  (list)))))})

(defmethod mutate 'cqrs.onyx.env/send-segments
  [env key {:keys [segments input-task] :or {input-task :in}}]
  {:action
   (fn []
     (swap! (:state env)
            (fn [state-stack]
              (let [latest-state (first state-stack)
                    next-state (update-in
                                 latest-state
                                 [:cqrs.onyx/env]
                                 onyx-setup/send input-task segments)]
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

(defmethod mutate 'cqrs.store/put
  [env key {:keys [store-key id obj] :as params}]
  {:value {:keys [store-key]}
   :action
   (fn []
     (swap! (:state env)
            (fn [state-stack]
              (let [state (first state-stack)
                    store (get state store-key)
                    next-store (store/put store id obj)
                    next-state (assoc
                                 state
                                 store-key next-store)
                    next-stack (conj state-stack next-state)]
                next-stack))))})

(defmethod mutate :default
  [env key params]
    {:value {:keys [:cqrs.onyx/env]}
     :action
     (fn []
       (let [onyx-fn ({'cqrs.onyx.env/tick onyx/tick
                       'cqrs.onyx.env/drain onyx/drain
                       'cqrs.onyx.env/stop onyx/stop} key)
             state-stack @(:state env)
             state (first state-stack)
             onyx-env (:cqrs.onyx/env state)
             next-onyx-env (onyx-fn onyx-env)
             ;; Ticking onyx env might case cascading changes to state. Aggregation sync function also mutate state.
             ]
       (swap! (:state env)
              ;; Since env is an atom, inside swap we get the latest state after cascading mutations.
              ;; We are careful to only update onyx env and not other parts that might have changed during ticking.
              (fn [state-stack]
                (let [state (first state-stack)
                      next-state (assoc
                                   state
                                   :cqrs.onyx/env next-onyx-env)]
                  (conj state-stack next-state))))))})


(defn reconciler
  []
  (om/reconciler
    {:state (init-state-stack)
     :parser (om/parser {:read read :mutate mutate})}))
