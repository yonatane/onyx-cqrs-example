(ns onyx-cqrs-example.aggregation.event-sourcing
  (:require [cljs.pprint :refer [pprint]]
            [onyx-cqrs-example.global-reconciler :as global-reconciler]
            [om.next :as om]))


(defn wrap-init
  [f]
  (fn
    [window]
    {::events []
     ::aggregate-state (f window)}))

(defn wrap-create-state-update
  [f]
  (fn
    [window {::keys [aggregate-state]} command]
    {::events-update ::conj-event
     ::event (f window aggregate-state command)}))

(defn wrap-apply-state-update
  [f]
  (fn
    [window {::keys [aggregate-state] :as state} {::keys [events-update event]}]
      (cond-> state

              (= ::conj-event events-update)
              (update ::events conj event)

              :always
              (update ::aggregate-state #(f window aggregate-state event)))))


(defn refinement-create-state-update
  [trigger state state-event]
  ::discard-persisted)

(defn refinement-apply-state-update
  [trigger state entry]
  (case entry
    ::discard-persisted
    (update state ::events empty)

    ;; else
    state))

(def refinement
  {:refinement/create-state-update refinement-create-state-update
   :refinement/apply-state-update refinement-apply-state-update})


(defn sync
  [task-event window trigger state-event state]
  (let [;; workaround for onyx bug where task-event is nil: we just get the global reconciler ourselves.
        task-event (assoc
                     task-event
                     :cqrs.app-state/reconciler (global-reconciler/get))
        reconciler (:cqrs.app-state/reconciler task-event)
        {::keys [events]} state]
    (doseq [event events]
        (om/transact! reconciler `[(cqrs.store/put {:store-key :cqrs/event-store
                                                    :id ~(:event/id event)
                                                    :obj ~event})
                                   :cqrs/event-store]))))


(defn wrap-aggregation
  [init create-state-update apply-state-update]
  {:aggregation/init (wrap-init init)
   :aggregation/create-state-update (wrap-create-state-update create-state-update)
   :aggregation/apply-state-update (wrap-apply-state-update apply-state-update)})
