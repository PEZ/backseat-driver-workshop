(ns acme.frontend.views)

(def lang-flags
  {:EN "\uD83C\uDDEC\uD83C\uDDE7"
   :ES "\uD83C\uDDEA\uD83C\uDDF8"
   :DE "\uD83C\uDDE9\uD83C\uDDEA"
   :FR "\uD83C\uDDEB\uD83C\uDDF7"
   :IT "\uD83C\uDDEE\uD83C\uDDF9"
   :ZH-HANS "\uD83C\uDDE8\uD83C\uDDF3"
   :ZH-HANT "\uD83C\uDDF9\uD83C\uDDFC"})

(defn search-bar [{:keys [search-text]}]
  [:div.search-bar
   [:input {:type "text"
            :placeholder "Search cocktails..."
            :value search-text
            :on {:input [[:ui/ax.update-search-text :event/target.value]]}}]])

(defn category-pills [{:keys [categories selected-category]}]
  (into [:div.category-pills]
        (map (fn [cat]
               [:button {:replicant/key cat
                         :class (str "pill" (when (= cat selected-category) " active"))
                         :on {:click [[:ui/ax.filter-by-category cat]]}}
                cat]))
        categories))

(defn drink-card [{:keys [id name thumbnail]}]
  [:div.drink-card {:replicant/key id
                    :on {:click [[:ui/ax.select-drink id]]}}
   (when thumbnail
     [:img {:src thumbnail :alt name :loading "lazy"}])
   [:div.card-name name]])

(defn drink-grid [{:keys [drinks]}]
  (into [:div.drink-grid]
        (map drink-card)
        drinks))

(defn percentage-indicator [{:keys [filtered-percentage total-drinks drink-count]}]
  (when (pos? total-drinks)
    [:div.percentage-indicator
     [:div.percentage-text
      (str drink-count " / " total-drinks)]
     [:div.percentage-bar
      [:div.percentage-fill {:style {:width (str filtered-percentage "%")}}]]]))

(defn drink-detail [{:keys [drink loading? selected-lang]}]
  (cond
    loading?
    [:div.detail-overlay
     [:div.detail-card
      [:button.close-btn {:on {:click [[:ui/ax.close-detail]]}} "\u00D7"]
      [:div.detail-loading
       [:p "Loading cocktail details..."]]]]

    drink
    (let [translations (:translations drink)
          lang (or selected-lang "EN")
          instructions (get-in translations [lang :instructions] (:instructions drink))
          available-langs (sort (keys translations))]
      [:div.detail-overlay
       [:div.detail-card
        [:button.close-btn {:on {:click [[:ui/ax.close-detail]]}} "\u00D7"]
        [:div.detail-header
         (when (:thumbnail drink)
           [:img {:src (:thumbnail drink) :alt (:name drink)}])
         [:div
          [:h2 (:name drink)]
          (when (:category drink) [:span.category-badge (:category drink)])]]
        [:div.detail-body
         [:h3 "Ingredients"]
         (into [:ul.ingredients-list]
               (map-indexed (fn [i {:keys [name measure]}]
                              [:li {:replicant/key i} (str (when measure (str measure " ")) name)]))
               (:ingredients drink))
         (when instructions
           [:div
            [:h3 "Instructions"]
            (when (> (count available-langs) 1)
              (into [:div.lang-flags]
                    (map (fn [l]
                           [:button {:replicant/key l
                                     :class (str "lang-flag" (when (= l lang) " active"))
                                     :on {:click [[:ui/ax.set-detail-language l]]}
                                     :title l}
                            (get lang-flags l l)]))
                    available-langs))
            [:p instructions]])]]])))

(defn error-banner [{:keys [message]}]
  (when message
    [:div.error-banner {:on {:click [[:ui/ax.dismiss-error]]}}
     [:span message]
     [:button.dismiss-btn "✕"]]))

(defn app-view [state]
  [:div.app
   [:header.app-header
    [:h1 "\uD83C\uDF78 Cocktail Explorer"]
    (search-bar {:search-text (:ui/search-text state)})]
   (error-banner {:message (:ui/error-message state)})
   (category-pills {:categories (:data/categories state)
                     :selected-category (:ui/selected-category state)})
   (percentage-indicator {:filtered-percentage (:viz/filtered-percentage state)
                          :total-drinks (:viz/total-drinks-available state)
                          :drink-count (count (:data/drinks state))})
   [:main.app-main
    (when (seq (:data/drinks state))
      (drink-grid {:drinks (:data/drinks state)}))]
   (drink-detail {:drink (:data/selected-drink state)
                  :loading? (some? (:ui/loading-drink-id state))
                  :selected-lang (:ui/detail-language state)})])
