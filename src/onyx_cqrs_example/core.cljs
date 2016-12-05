(ns onyx-cqrs-example.core
  (:require [cljs.pprint :refer [pprint]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [onyx-local-rt.api :as onyx]))


(enable-console-print!)


(defn ^:export init-account [window]
  0)

(defn ^:export process-account-command [window state command]
  (case (:command/type command)
    :command.type/create-account
    {:event/type :event.type/account-created}

    :command.type/deposit
    {:event/type :event.type/funds-modified
     :funds.modify/amount (:deposit/amount command)}

    :command.type/withdraw
    {:event/type :event.type/funds-modified
     :funds.modify/amount (- (:withdraw/amount command))}))

(defn ^:export apply-account-event [window state event]
  (case (:event/type event)
    :event.type/account-created
    0

    :event.type/funds-modified
    (+ state (:funds.modify/amount event))))

(defn ^:export dump-aggregate [event window trigger opts state]
  (println "dump-aggregate")
  (println "state")
  (pprint state)
  (println "event")
  (pprint event))

(def ^:export account-aggregation
  {:aggregation/init init-account
   :aggregation/create-state-update process-account-command
   :aggregation/apply-state-update apply-account-event})


(def job
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
     :window/aggregation ::account-aggregation}]

   :triggers
   [{:trigger/window-id :window.id/account
     :trigger/on :onyx.triggers/segment
     :trigger/threshold [1 :elements]
     :trigger/refinement :onyx.refinements/accumulating
     :trigger/sync ::dump-aggregate}]
   })


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

(defn init-env [job]
  (reduce
    (fn [onyx-env segment]
      (onyx/new-segment onyx-env :in segment))
    (onyx/init job)
    commands))

(defn init-envs [job]
  (list (init-env job)))


(def init-data
  {:cqrs.onyx/envs
   (init-envs job)})


(defui EnvSummary
  static om/IQuery
  (query [this]
    [:cqrs.onyx/envs])
  Object
  (render [this]
    (let [{:keys [cqrs.onyx/envs]} (om/props this)
          onyx-env (first envs)]
      (dom/div nil
               (dom/h3 nil "Env Summary:")
               (dom/div
                 nil
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.env/reset)]))}
                   "Reset")
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.env/revert)]))}
                   "<-Revert")
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.env/tick)]))}
                   "Tick->")
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.env/drain)]))}
                   "Drain")
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.env/stop)]))}
                   "Stop"))
               (dom/textarea
                 #js {:className "env-summary"
                      :value (with-out-str (pprint (onyx/env-summary onyx-env)))})))))


(defn read
  [env key params]
  (let [st @(:state env)]
    {:value (get st key)}))


(defmulti mutate om/dispatch)

(defmethod mutate 'cqrs.env/reset
  [env key params]
  {:action
   (fn []
     (swap! (:state env)
            update-in [:cqrs.onyx/envs]
            #(init-envs job)))})

(defmethod mutate 'cqrs.env/revert
  [env key params]
  {:action
   (fn []
     (swap! (:state env)
            update-in [:cqrs.onyx/envs]
            (fn [envs]
              (if-let [prev-envs (next envs)]
                prev-envs
                envs))))})

(defmethod mutate :default
  [env key params]
  (let [next-env ({'cqrs.env/tick onyx/tick
                   'cqrs.env/drain onyx/drain
                   'cqrs.env/stop onyx/stop} key)]
    {:action
     (fn []
       (swap! (:state env)
              update-in [:cqrs.onyx/envs]
              (fn [envs]
                (let [current (first envs)]
                  (conj envs (next-env current))))))}))

(def reconciler
  (om/reconciler
    {:state init-data
     :parser (om/parser {:read read :mutate mutate})}))

(om/add-root! reconciler
              EnvSummary (gdom/getElement "app"))
