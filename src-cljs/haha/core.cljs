(ns haha.core
  (:require-macros
    [cljs.core.async.macros :refer [go]])
    (:require 
      [ajax.core :refer [GET POST]]
      [cljs-http.client :as http]
      [cljs.core.async :refer [<! take!]]
      [reagent.core :as r]
      [reagent.dom  :as rdom]))

(enable-console-print!)

(println "reload")

;;We need a `defonce` because the namespace is re-initialized on every reload
(defonce app-state (r/atom 0))

(defn simple-component [prop]
  [:div
   [:p "I am a component and I have been reloaded " (str  prop) " time"]])


(defn compound-component
  []
  [:p
   [simple-component @app-state]
   [:button {:on-click #(swap! app-state inc)} "Click to increment"]
   [:br]
   [:button {:on-click #(reset! app-state 0)} "Click to Reset"]])



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


(defn x []
  #_(swap! state-atom
          assoc
          :clicked-index
          (aget % "target" "value"))
)

(defn show-results
  [state-atom]
  (when (not (empty? (:results @state-atom)))
   [:ul
    (map-indexed (fn [idx item]
                   (let [css-class (if (and (:clicked-index @state-atom)
                                            (= (:clicked-index @state-atom) idx))
                                     "clickedlistbuttonitem"
                                     "listbuttonitem")
                         attrs {:on-click #(swap! state-atom assoc :clicked-index idx)
                                :class    css-class}]
                     ^{:key idx}  
                     [:li attrs item]))
                 (:results @state-atom))]))

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
        [:div 
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
       [:button.clearbutton "clear"]
       [:div
        [show-results s]]]
      )))

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

