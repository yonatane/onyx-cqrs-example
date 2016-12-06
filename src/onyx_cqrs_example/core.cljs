(ns onyx-cqrs-example.core
  (:require [cljs.pprint :refer [pprint]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [onyx-local-rt.api :as onyx]
            [onyx-cqrs-example.job :as example-job]))


(enable-console-print!)


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
  {:cqrs/command-store
   {}

   :cqrs/event-store
   {}

   :cqrs.onyx/envs
   (init-envs (example-job/create))})


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
            #(init-envs (example-job/create))))})

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
