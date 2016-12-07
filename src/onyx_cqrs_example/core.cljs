(ns onyx-cqrs-example.core
  (:require [cljs.pprint :refer [pprint]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [onyx-local-rt.api :as onyx]
            [onyx-cqrs-example.app-state :as app-state]
            [onyx-cqrs-example.global-reconciler :as global-reconciler]))


(enable-console-print!)


(global-reconciler/init (app-state/reconciler))


(defui EnvSummary
  static om/IQuery
  (query [this]
    [:cqrs.onyx/env
     :cqrs.scenario/commands
     :cqrs/event-store])
  Object
  (render [this]
    (let [{onyx-env :cqrs.onyx/env
           commands :cqrs.scenario/commands
           event-store :cqrs/event-store}
          (om/props this)]
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
                      :value (with-out-str (pprint (onyx/env-summary onyx-env)))})
               (dom/textarea
                 #js {:className "event-store"
                      :value (with-out-str (pprint event-store))})))))


(om/add-root! (global-reconciler/get)
              EnvSummary (gdom/getElement "app"))
