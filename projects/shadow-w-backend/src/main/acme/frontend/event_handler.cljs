(ns acme.frontend.event-handler
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [acme.frontend.db :as db]
            [acme.frontend.actions-handler :as actions-handler]
            [acme.frontend.effects :as effects]))

;; -- Enrichment --

(defn- js-get-in [obj path]
  (reduce (fn [acc k] (when (some? acc) (unchecked-get acc k)))
          obj path))

(defn- shallow-event-map [^js e]
  {:type (.-type e) :key (.-key e)
   :target.value (some-> e .-target .-value)
   :clientX (.-clientX e) :clientY (.-clientY e)})

(defn- enrich-from-replicant-data
  [{:replicant/keys [js-event node]} actions]
  (walk/postwalk
   (fn [x]
     (cond
       (keyword? x)
       (cond
         (= :event/event x) (shallow-event-map js-event)
         (= :event/target.value x) (some-> js-event .-target .-value)
         (= :dom/node x) node
         (= "event" (namespace x)) (js-get-in js-event (str/split (name x) #"\."))
         :else x)

       (and (vector? x) (= :dom/element-by-id (first x)))
       (js/document.getElementById (second x))

       :else x))
   actions))

(defn- enrich-from-state [state action]
  (walk/postwalk
   (fn [x]
     (if (and (vector? x) (= :db/get (first x)))
       (get state (second x))
       x))
   action))

(defn enrich-action [state replicant-data action]
  (->> action
       (enrich-from-state state)
       (#(enrich-from-replicant-data replicant-data %))))

;; -- Async Helpers --

(defn- await-fx? [fx]
  (and (vector? fx) (= :uf/await (first fx))))

(defn- unwrap-fx [fx]
  (if (await-fx? fx) (vec (rest fx)) fx))

(defn- replace-prev-result [form prev-result]
  (walk/postwalk
   (fn [x] (if (= :uf/prev-result x) prev-result x))
   form))

(defn- replace-prev-result-in-actions [actions prev-result]
  (mapv #(replace-prev-result % prev-result) actions))

;; -- Effect Execution --

(defn- execute-effect! [dispatch fx]
  (let [result (effects/perform-effect! dispatch fx)]
    (if (= :uf/unhandled-fx result)
      (let [generic (effects/generic-perform-effect! dispatch fx)]
        (when (= :uf/unhandled-fx generic)
          (js/console.warn "Unhandled effect:" fx))
        generic)
      result)))

(defn- execute-effects! [dispatch fxs]
  (reduce
   (fn [promise-chain raw-fx]
     (.then promise-chain
            (fn [prev-result]
              (let [is-await? (await-fx? raw-fx)
                    fx (-> raw-fx unwrap-fx (replace-prev-result prev-result))
                    result (execute-effect! dispatch fx)]
                (if is-await?
                  (-> (js/Promise.resolve result)
                      (.then (fn [r] r)))
                  prev-result)))))
   (js/Promise.resolve nil)
   fxs))

;; -- Dispatch Loop --

(defn dispatch!
  ([actions] (dispatch! actions nil))
  ([actions replicant-data]
   (let [old-state @db/!state
         uf-data {:system/now (.now js/Date)
                  :uf/replicant-data replicant-data
                  :uf/enrich-fn (fn [state action] (enrich-action state replicant-data action))}
         {:uf/keys [db fxs dxs]} (actions-handler/handle-actions old-state uf-data actions)
         dispatch-fn (fn [more] (dispatch! more replicant-data))]
     (when (some? db)
       (reset! db/!state db))
     (if (seq fxs)
       (-> (execute-effects! dispatch-fn (remove nil? fxs))
           (.then (fn [prev-result]
                    (when (seq dxs)
                      (let [substituted (replace-prev-result-in-actions dxs prev-result)]
                        (dispatch-fn substituted))))))
       (when (seq dxs)
         (dispatch-fn dxs))))))

(defn event-handler
  "Replicant dispatch entry point."
  [replicant-data actions]
  (dispatch! actions replicant-data))
