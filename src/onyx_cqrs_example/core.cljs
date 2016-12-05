(ns onyx-cqrs-example.core
  (:require [cljs.pprint :refer [pprint]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [onyx-local-rt.api :as onyx]))


(enable-console-print!)


(def job
  {:workflow [[:in :inc] [:inc :out]]
   :catalog [{:onyx/name :in
              :onyx/type :input
              :onyx/batch-size 20}
             {:onyx/name :inc
              :onyx/type :function
              :onyx/fn ::my-inc
              :onyx/batch-size 20}
             {:onyx/name :out
              :onyx/type :output
              :onyx/batch-size 20}]
   :lifecycles []})

(def commands
  [{:command/type :command.type/inc
    :n 41}
   {:command/type :command.type/inc
    :n 84}])


(def init-data
  {:cqrs.onyx/env
   (onyx/init {})})


(defui EnvSummary
  static om/IQuery
  (query [this]
    [:cqrs.onyx/env])
  Object
  (render [this]
    (let [{:keys [cqrs.onyx/env]} (om/props this)]
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
                      :value (with-out-str (pprint (onyx/env-summary env)))})))))


;; ^:export the function if using in ClojureScript.
(defn ^:export my-inc [segment]
  (update-in segment [:n] inc))


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
            update-in [:cqrs.onyx/env]
            #(reduce
               (fn [onyx-env segment]
                 (onyx/new-segment onyx-env :in segment))
               (onyx/init job)
               commands)))})

(defmethod mutate :default
  [env key params]
  (let [onyx-env-fn ({'cqrs.env/tick onyx/tick
                      'cqrs.env/drain onyx/drain
                      'cqrs.env/stop onyx/stop} key)]
    {:action
     (fn []
       (swap! (:state env)
              update-in [:cqrs.onyx/env]
              onyx-env-fn))}))

(def reconciler
  (om/reconciler
    {:state init-data
     :parser (om/parser {:read read :mutate mutate})}))

(om/add-root! reconciler
              EnvSummary (gdom/getElement "app"))
