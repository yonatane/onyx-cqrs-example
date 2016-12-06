(ns onyx-cqrs-example.account-aggregation)


(defn ^:export init-account [window]
  0)

(defn ^:export account-create-state-update [window state command]
  (case (:command/type command)
    :command.type/create-account
    {:event/type :event.type/account-created}

    :command.type/deposit
    {:event/type :event.type/funds-modified
     :funds.modify/amount (:deposit/amount command)}

    :command.type/withdraw
    {:event/type :event.type/funds-modified
     :funds.modify/amount (- (:withdraw/amount command))}))

(defn ^:export account-apply-state-update [window state change]
  (case (:event/type change)
    :event.type/account-created
    0

    :event.type/funds-modified
    (+ state (:funds.modify/amount change))))


(def ^:export aggregation
  {:aggregation/init init-account
   :aggregation/create-state-update account-create-state-update
   :aggregation/apply-state-update account-apply-state-update})
