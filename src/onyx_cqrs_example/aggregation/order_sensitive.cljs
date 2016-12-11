(ns onyx-cqrs-example.aggregation.order-sensitive
  (:require [cljs.pprint :refer [pprint]]
            [onyx-cqrs-example.global-reconciler :as global-reconciler]
            [om.next :as om]))


(defn wrap-init
  [f]
  (fn
    [window]
    {::next-command-offset 0
     ::unhandled-commands []
     ::events []
     ::aggregate-state (f window)}))

(defn next-unhandled-batch [commands]
  (let [sorted-commands (sort-by :command/offset commands)
        expected-offsets (-> (first sorted-commands)
                             :command/offset
                             (drop (range)))]
    (->> expected-offsets
         (map vector sorted-commands)
         (take-while (fn [command offset] (= offset (:command/offset command))))
         (map first))))

;TODO: can I return nil for no-op? check onyx source code.
(defn wrap-create-state-update
  [f g]
  (fn
    [window
     {::keys [aggregate-state next-command-offset] :as state}
     {:keys [command/offset] :as command}]
          (cond
            (< offset next-command-offset)
            {:update/type ::no-op
             :update/reason "Already handled"
             :update/command command}

            (> offset next-command-offset)
            {:update/type ::waiting-for-missing
             :update/command command}

            (= offset next-command-offset)
            (let [batch (-> (::unhandled-commands state)
                            (conj command)
                            (next-unhandled-batch))]
              (reduce
                (fn [batch-update unhandled-command]
                  (let [{:update/keys [aggregate-state events]} batch-update
                        event (f window aggregate-state unhandled-command)]
                    (-> batch-update
                        (update :update/aggregate-state #(g window aggregate-state event))
                        (update :update/events conj event)
                        (update :update/next-command-offset inc))))
                {:update/type ::batch-handled
                 :update/aggregate-state aggregate-state
                 :update/events []
                 :update/next-command-offset next-command-offset}
                batch))

            :else
            {:update/type ::no-op
             :update/reason "Undefined behavior"
             :update/command command})))

(defn apply-state-update-wrapper
  [window
   state
   {:update/keys [type] :as change}]
  (cond
    (= ::no-op type)
    ;TODO: log reason and command in a textarea
    state

    (= ::waiting-for-missing type)
    (update state ::unhandled-commands conj (:update/command change))

    (= ::batch-handled type)
    (let [{:update/keys [events next-command-offset aggregate-state]} change]
      (-> state
          (update ::events into events)
          (update ::unhandled-commands (partial remove #(< (:command/offset %) next-command-offset)))
          (assoc ::next-command-offset next-command-offset)
          (assoc ::aggregate-state aggregate-state)))))



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
        {::keys [events aggregate-state]} state]
    (doseq [event events]
      (om/transact! reconciler `[(cqrs.store/put {:store-key :cqrs/event-store
                                                  :id ~(:event/id event)
                                                  :obj ~event})
                                 :cqrs/event-store]))))


(defn wrap-aggregation
  [init create-state-update apply-state-update]
  {:aggregation/init (wrap-init init)
   :aggregation/create-state-update (wrap-create-state-update create-state-update apply-state-update)
   :aggregation/apply-state-update apply-state-update-wrapper})
