(ns acme.frontend.app
  (:require [acme.frontend.db :as db]
            [acme.frontend.event-handler :refer [event-handler dispatch!]]
            [acme.frontend.views :refer [app-view]]
            [replicant.dom :as r-dom]))

(defn render! []
  (r-dom/render
   (js/document.getElementById "root")
   (app-view @db/!state)))

(defonce initialized? (atom false))

(defn ^:dev/after-load reload []
  (render!))

(defn init []
  (when-not @initialized?
    (reset! initialized? true)
    (r-dom/set-dispatch! event-handler)
    (add-watch db/!state ::render
               (fn [_ _ old new]
                 (when (not= old new) (render!))))
    (render!)
    (dispatch! [[:app/ax.init]])))

(comment
  ;; Search
  (dispatch! [[:ui/ax.search-drinks "margarita"]])
  (dispatch! [[:ui/ax.search-drinks ""]])

  ;; Filter by category
  (dispatch! [[:ui/ax.filter-by-category "Shot"]])

  ;; Inspect state
  @db/!state
  :rcf)