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
(defonce app-state (r/atom {}))

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
    [el/render-task-list state-atom]))

(defn search-form
  []
  (let [
        set-in-s (fn [event k] (swap! app-state assoc k (-> event .-target .-value)))]
    (fn render []
      [:div
       [:form {:on-submit (fn [e]
                            (println "button clicked")
                            (.preventDefault e)
                            (form-search @app-state app-state))}

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
                                         (swap! app-state dissoc :results))}
        "clear"]
       [:div
        [show-results app-state]]])))

(defn loading-form
  [state-atom]
  (when (not (:results @state-atom))
    [:h1 "Loading"])
  [:div
   [show-results app-state]])

(defn new-list-banner
  []
  (let [;;r/current-component is empty inside the
        ;;click handler, so get the reference to this in the let
        component (r/current-component)
        handler (fn [e]
                  (let [dom-node  (rdom/dom-node component)
                        _         (form-search @app-state app-state)]
                    (rdom/render
                      [loading-form app-state]
                      (js/document.getElementById "app"))))]
    [:h1 {:on-click handler} "Click to create a new list"]))

;;TODO add a 
;;(set! (.-onclick js/document) (fn [e]))
;;to set an event handler to stop editing our todo items
;;in case we click outside of our div

(defn element-contains?
  "is a parent of b"
  [element test-element]
  (println  (. element contains test-element)))

(defn run []
  ;;Handle clicks outside the container of our react app, by using a
  ;;default handler to set values in the state if the click originates
  ;;outside the container
  (set! (.-onclick  js/document) (fn [e] 
                                   (element-contains? (. js/document getElementById "app") (.-target e))))

  (rdom/render [new-list-banner] (js/document.getElementById "app")))

;; When the browser loads the JS, it evaluates all the forms in the namespace,
;; To "launch" our application, call run
(run)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  #_(do
      (println "when does this happen")
      (swap! app-state inc)))

