(ns onyx-cqrs-example.core
  (:require [cljs.pprint :refer [pprint]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [onyx-local-rt.api :as onyx]
            [onyx-cqrs-example.app-state :as app-state]
            [onyx-cqrs-example.onyx :as onyx-setup]
            [onyx-cqrs-example.store :as store]))


(enable-console-print!)


(defui EnvSummary
  static om/IQuery
  (query [this]
    [:cqrs.onyx/env :cqrs.scenario/commands])
  Object
  (render [this]
    (let [{onyx-env :cqrs.onyx/env commands :cqrs.scenario/commands} (om/props this)]
      (dom/div nil
               (dom/h3 nil "Env Summary:")
               (dom/div
                 nil
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.onyx.env/send-commands {:commands ~commands})]))}
                   "Send!")
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.state/reset)]))}
                   "Reset")
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.state/revert)]))}
                   "<-Revert")
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.onyx.env/tick)]))}
                   "Tick->")
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.onyx.env/drain)]))}
                   "Drain")
                 (dom/button
                   #js {:onClick
                        (fn [e]
                          (om/transact! this `[(cqrs.onyx.env/stop)]))}
                   "Stop"))
               (dom/textarea
                 #js {:className "env-summary"
                      :value (with-out-str (pprint (onyx/env-summary onyx-env)))})))))


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

(def reconciler
  (om/reconciler
    {:state (app-state/create)
     :parser (om/parser {:read read :mutate mutate})}))

(om/add-root! reconciler
              EnvSummary (gdom/getElement "app"))
