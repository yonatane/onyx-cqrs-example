(ns onyx-cqrs-example.account
  (:require [cljs.pprint :refer [pprint]]
            [om.next :as om]
            [onyx-cqrs-example.global-reconciler :as global-reconciler]))


(defn init [window]
  {:events []
   :aggregate nil})

(defn create-state-update [window state command]
  (case (:command/type command)
    :command.type/create-account
    {:event/type :event.type/account-created
     :event/id (:command/id command)
     :account/id (:account/id command)}

    :command.type/deposit
    {:event/type :event.type/funds-modified
     :event/id (:command/id command)
     :funds.modify/amount (:deposit/amount command)}

    :command.type/withdraw
    {:event/type :event.type/funds-modified
     :event/id (:command/id command)
     :funds.modify/amount (- (:withdraw/amount command))}

    ;; else
    {:event/type :event.type/unknown-command
     :event/id (:command/id command)}))

(defn apply-state-update [window state change]
  (let [next-state (update state :events conj change)]
    (case (:event/type change)
      :event.type/account-created
      (assoc
        next-state
        :aggregate 0)

      :event.type/funds-modified
      (update
        next-state
        :aggregate
        + (:funds.modify/amount change))

      next-state)))


(def ^:export aggregation
  {:aggregation/init init
   :aggregation/create-state-update create-state-update
   :aggregation/apply-state-update apply-state-update})


(defn ^:export sync-aggregation [task-event window trigger state-event state]
  (let [;; workaround for onyx bug where task-event is nil: we just get the global reconciler ourselves.
        task-event (assoc
                     task-event
                     :cqrs.app-state/reconciler (global-reconciler/get))
        reconciler (:cqrs.app-state/reconciler task-event)
        {:keys [events aggregate]} state]
    (doseq [event events]
      (try
        (om/transact! reconciler `[(cqrs.store/put
                                     {:store-key :cqrs/event-store
                                      :id ~(:event/id event)
                                      :obj ~event})
                                   :cqrs/event-store])
        (catch js/Error e (pprint e))))))
