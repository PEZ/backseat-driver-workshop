(ns acme.frontend.actions-handler
  (:require [acme.frontend.actions :as actions]))

(defn generic-handle-action [state _uf-data [action & args]]
  (case action
    :db/ax.assoc {:uf/db (apply assoc state args)}
    :uf/unhandled-ax))

(defn handle-actions [state uf-data actions]
  (reduce
   (fn [{:uf/keys [db] :as acc} action]
     (let [result (actions/handle-action db uf-data action)
           result (if (= :uf/unhandled-ax result)
                    (let [generic (generic-handle-action db uf-data action)]
                      (when (= :uf/unhandled-ax generic)
                        (js/console.warn "Unhandled action:" action))
                      generic)
                    result)
           {:uf/keys [db fxs dxs]} (when (map? result) result)]
       (cond-> acc
         db (assoc :uf/db db)
         (seq fxs) (update :uf/fxs into fxs)
         (seq dxs) (update :uf/dxs into dxs))))
   {:uf/db state :uf/fxs [] :uf/dxs []}
   (remove nil? actions)))

