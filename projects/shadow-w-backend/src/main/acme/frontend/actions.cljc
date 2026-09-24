(ns acme.frontend.actions)

(defn- encode-uri-component [s]
  #?(:cljs (js/encodeURIComponent s)
     :clj (java.net.URLEncoder/encode s "UTF-8")))

(defn compute-ingredient-counts [drinks]
  (->> drinks
       (mapcat :ingredients)
       (map :name)
       (filter some?)
       frequencies
       (sort-by val >)
       (take 15)
       (into {})))

(defn calculate-percentage [filtered-count total-count]
  (if (and (pos? total-count) (>= filtered-count 0))
    (* (/ filtered-count total-count) 100.0)
    0.0))

(defn handle-action [state uf-data action]
  (let [enrich-fn (get uf-data :uf/enrich-fn (fn [_state a] a))
        [op & args] (enrich-fn state action)]
    (case op
      :ui/ax.update-search-text
      (let [[text] args
            old-timer-id (:ui/debounce-timer-id state)]
        {:uf/db (assoc state :ui/search-text text)
         :uf/fxs [[:uf/await :ui/fx.debounced-search text old-timer-id]]
         :uf/dxs [[:ui/ax.store-debounce-timer :uf/prev-result]]})

      :ui/ax.search-drinks
      (let [[query] args
            category (:ui/selected-category state)
            new-gen (inc (:data/search-generation state 0))]
        (if (seq query)
          (let [url (if category
                      (str "/api/search?q=" (encode-uri-component query)
                           "&c=" (encode-uri-component category))
                      (str "/api/search?q=" (encode-uri-component query)))]
            {:uf/db (assoc state :ui/search-text query :data/search-generation new-gen)
             :uf/fxs [[:uf/await :http/fx.fetch-json url]]
             :uf/dxs [[:data/ax.receive-search-results :uf/prev-result new-gen]]})
          (let [url (if category
                      (str "/api/filter?c=" (encode-uri-component category))
                      "/api/filter")]
            {:uf/db (assoc state :ui/search-text "" :data/search-generation new-gen)
             :uf/fxs [[:uf/await :http/fx.fetch-json url]]
             :uf/dxs [[:data/ax.receive-initial-drinks :uf/prev-result new-gen]]})))

      :data/ax.receive-search-results
      (let [[drinks gen] args
            current-gen (:data/search-generation state)]
        (if (and gen (not= gen current-gen))
          {:uf/db state}
          (let [total (:viz/total-drinks-available state)
                percentage (calculate-percentage (count drinks) total)]
            {:uf/db (assoc state
                           :data/drinks drinks
                           :viz/filtered-percentage percentage
                           :viz/ingredient-counts (compute-ingredient-counts drinks))})))

      :ui/ax.filter-by-category
      (let [[category] args
            current-category (:ui/selected-category state)
            new-category (if (= category current-category) nil category)
            search-text (:ui/search-text state)
            new-gen (inc (:data/search-generation state 0))]
        (if new-category
          (if (seq search-text)
            (let [url (str "/api/search?q=" (encode-uri-component search-text)
                           "&c=" (encode-uri-component new-category))]
              {:uf/db (assoc state :ui/selected-category new-category :data/search-generation new-gen)
               :uf/fxs [[:uf/await :http/fx.fetch-json url]]
               :uf/dxs [[:data/ax.receive-search-results :uf/prev-result new-gen]]})
            (let [url (str "/api/filter?c=" (encode-uri-component new-category))]
              {:uf/db (assoc state :ui/selected-category new-category :data/search-generation new-gen)
               :uf/fxs [[:uf/await :http/fx.fetch-json url]]
               :uf/dxs [[:data/ax.receive-filtered-drinks :uf/prev-result new-gen]]}))
          ;; Deselected - go back to all drinks or search results
          (if (seq search-text)
            {:uf/db (assoc state :ui/selected-category nil :data/search-generation new-gen)
             :uf/fxs [[:uf/await :http/fx.fetch-json (str "/api/search?q=" (encode-uri-component search-text))]]
             :uf/dxs [[:data/ax.receive-search-results :uf/prev-result new-gen]]}
            {:uf/db (assoc state :ui/selected-category nil :data/search-generation new-gen)
             :uf/fxs [[:uf/await :http/fx.fetch-json "/api/filter"]]
             :uf/dxs [[:data/ax.receive-initial-drinks :uf/prev-result new-gen]]})))

      :data/ax.receive-filtered-drinks
      (let [[drinks gen] args
            current-gen (:data/search-generation state)]
        (if (and gen (not= gen current-gen))
          {:uf/db state}
          (let [total (:viz/total-drinks-available state)
                percentage (calculate-percentage (count drinks) total)]
            {:uf/db (assoc state
                           :data/drinks drinks
                           :viz/filtered-percentage percentage
                           :viz/ingredient-counts (compute-ingredient-counts drinks))})))

      :ui/ax.select-drink
      (let [[id] args]
        {:uf/db (assoc state :ui/loading-drink-id id)
         :uf/fxs [[:uf/await :http/fx.fetch-json (str "/api/drink/" id)]]
         :uf/dxs [[:data/ax.receive-drink-detail :uf/prev-result]]})

      :data/ax.receive-drink-detail
      (let [[drink] args]
        {:uf/db (assoc state
                       :data/selected-drink drink
                       :ui/loading-drink-id nil)})

      :ui/ax.set-detail-language
      (let [[lang] args]
        {:uf/db (assoc state :ui/detail-language lang)})

      :ui/ax.close-detail
      {:uf/db (assoc state :data/selected-drink nil :ui/loading-drink-id nil :ui/detail-language :EN)}

      :data/ax.receive-categories
      (let [[categories] args]
        {:uf/db (assoc state :data/categories categories)})

      :data/ax.receive-total-drinks
      (let [[result] args
            total (:total result)]
        {:uf/db (assoc state :viz/total-drinks-available total)})

      :data/ax.receive-initial-drinks
      (let [[drinks gen] args
            current-gen (:data/search-generation state)]
        (if (and gen (not= gen current-gen))
          {:uf/db state}
          (let [total (:viz/total-drinks-available state)
                percentage (calculate-percentage (count drinks) total)]
            {:uf/db (assoc state
                           :data/drinks drinks
                           :viz/filtered-percentage percentage
                           :viz/ingredient-counts (compute-ingredient-counts drinks))})))

      :data/ax.receive-total-drinks-and-continue
      (let [[total-response] args
            total (:total total-response)]
        {:uf/db (assoc state :viz/total-drinks-available total)
         :uf/fxs [[:uf/await :http/fx.fetch-json "/api/categories"]]
         :uf/dxs [[:data/ax.receive-categories-and-continue :uf/prev-result]]})

      :data/ax.receive-categories-and-continue
      (let [[cats] args]
        {:uf/db (assoc state :data/categories cats)
         :uf/fxs [[:uf/await :http/fx.fetch-json "/api/filter"]]
         :uf/dxs [[:data/ax.receive-initial-drinks :uf/prev-result]]})

      :app/ax.init
      {:uf/fxs [[:uf/await :http/fx.fetch-json "/api/total-drinks"]]
       :uf/dxs [[:data/ax.receive-total-drinks-and-continue :uf/prev-result]]}

      :ui/ax.store-debounce-timer
      (let [[timer-id] args]
        {:uf/db (assoc state :ui/debounce-timer-id timer-id)})

      :ui/ax.show-error
      (let [[message] args]
        {:uf/db (assoc state :ui/error-message message)
         :uf/fxs [[:log/fx.log :error "App error:" message]]})

      :ui/ax.dismiss-error
      {:uf/db (dissoc state :ui/error-message)}

      :uf/unhandled-ax)))
