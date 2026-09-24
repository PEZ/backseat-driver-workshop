(ns acme.frontend.db)

(defonce !state
  (atom {:ui/search-text ""
         :ui/selected-category nil
         :ui/debounce-timer-id nil
         :ui/error-message nil
         :ui/loading-drink-id nil
         :ui/detail-language :EN
         :data/categories []
         :data/drinks []
         :data/selected-drink nil
         :data/search-generation 0
         :viz/ingredient-counts {}
         :viz/total-drinks-available 0
         :viz/filtered-percentage 100.0}))
