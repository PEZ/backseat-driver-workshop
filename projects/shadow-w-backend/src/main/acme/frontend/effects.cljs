(ns acme.frontend.effects)

(defn generic-perform-effect! [dispatch [effect & _args]]
  (case effect
    :uf/fx.dispatch (dispatch (first _args))
    :log/fx.log (apply js/console.log (rest _args))
    :uf/unhandled-fx))

(defn perform-effect! [dispatch [effect & args]]
  (case effect
    :http/fx.fetch-json
    (let [[url] args]
      (-> (js/fetch url)
          (.then (fn [res]
                   (if (.-ok res)
                     (.json res)
                     (throw (js/Error. (str "HTTP " (.-status res)))))))
          (.then #(js->clj % :keywordize-keys true))
          (.catch (fn [err]
                    (js/console.error "Fetch failed:" url err)
                    (dispatch [[:ui/ax.show-error (str "Failed to fetch data: " (.-message err))]])
                    nil))))

    :ui/fx.debounced-search
    (let [[text old-timer-id] args]
      (when old-timer-id
        (js/clearTimeout old-timer-id))
      (js/setTimeout
       (fn [] (dispatch [[:ui/ax.search-drinks text]]))
       300))

    :uf/unhandled-fx))
