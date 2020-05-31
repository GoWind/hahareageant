(ns haha.core
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   [ajax.core :refer [GET POST]]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<! take!]]
   [reagent.core :as r]
   [reagent.dom  :as rdom]
   [haha.expandedlist :as el]))

(enable-console-print!)

(println "reload")

;;We need a `defonce` because the namespace is re-initialized on every reload
(defonce app-state (r/atom 0))

(defn form-search
  [{:keys [pin location need] :as state} state-atom]
  (let [search-by (if (identity pin) pin location)
       ;;blocking operation
        search-resp (atom nil)
        params      (GET "http://localhost:3449/search"
                      {:params {"by"  search-by
                                "for" need}
                       :handler (fn
                                  [v]
                                  (swap! state-atom assoc :results v))})]))


(defn show-results
  [state-atom]
  (when (not (empty? (:results @state-atom)))
    (el/render-task-list state-atom)
    #_(tree-view (:results @state-atom))))

(defn search-form
  []
  (let [s        (r/atom {})
        set-in-s (fn [event k] (swap! s assoc k (-> event .-target .-value)))]
    (fn render []
      [:div
       [:form {:on-submit (fn [e]
                            (println "button clicked")
                            (.preventDefault e)
                            (form-search @s s))}

        [:div
         [:p "pin"]]
        [:div
         [:input {:type :text
                  :name :pin
                  :on-change #(set-in-s % :pin)}]
         [:p "or"]]
        [:divtext
         [:input {:type :text
                  :name :location
                  :on-change #(set-in-s % :location)}]
         [:br]]
        [:div
         [:p "And I am searching for"]
         [:input {:type :text
                  :name :need
                  :on-change #(set-in-s % :need)}]]
        [:div
         [:button.submitbutton "Submit"]]]
       [:button.clearbutton {:on-click (fn [e]
                                         (.preventDefault e)
                                         (swap! s dissoc :results))}
        "clear"]
       [:div
        [show-results s]]])))

(defn run []
  (rdom/render
   [search-form]
   #_[compound-component]
   (js/document.getElementById "app")))
;; When the browser loads the JS, it evaluates all the forms in the namespace,
;; To "launch" our application, call run
(run)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  #_(do
      (println "when does this happen")
      (swap! app-state inc)))

