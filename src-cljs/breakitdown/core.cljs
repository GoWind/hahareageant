(ns breakitdown.core
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   [ajax.core :refer [GET POST]]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<! take!]]
   [reagent.core :as r]
   [reagent.dom  :as rdom]
   [breakitdown.expandedlist :as el]
   [breakitdown.state :as bs]))

(enable-console-print!)

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
                                  (swap! state-atom assoc :results v))
                       :error-handler (fn [e]
                                        (swap! state-atom assoc :results (bs/load-from-session-storage)))})]))


(defn show-results
  [state-atom]
  (when (some? (:results @state-atom))
    [el/render-task-list state-atom]))


(defn loading-form
  [state-atom]
  (when (not (:results @state-atom))
    [:h1 "Loading"])
  [:div
   [show-results bs/app-state]])

(defn new-list-banner
  "Show a banner, which when clicked on, 
   shows a new list"
  []
  (let [;;r/current-component is empty inside the
        ;;click handler, so get the reference to this in the let
        component (r/current-component)
        handler (fn [e]
                  (let [dom-node  (rdom/dom-node component)
                        _         (form-search @bs/app-state bs/app-state)]
                    (rdom/render
                      [loading-form bs/app-state]
                      (js/document.getElementById "app"))))]
    [:h1 {:on-click handler} "Click to create a new list"]))

;;TODO add a 
;;(set! (.-onclick js/document) (fn [e]))
;;to set an event handler to stop editing our todo items
;;in case we click outside of our div

(defn element-contains?
  "is a parent of b"
  [element test-element]
  (. element contains test-element))

(defn run []
  ;;Handle clicks outside the container of our react app, by using a
  ;;default handler to set values in the state if the click originates
  ;;outside the container
  (set! (.-onclick  js/document) (fn [e] 
                                   (if (not (element-contains? (. js/document getElementById "app") (.-target e)))
                                     ;;TODO: this really leaks abstraction,
                                     ;;figure out a better way to handle this
                                     (swap! bs/app-state dissoc :edit))))
  (form-search @bs/app-state bs/app-state)
  (rdom/render [loading-form bs/app-state] (js/document.getElementById "app")))

;; When the browser loads the JS, it evaluates all the forms in the namespace,
;; To "launch" our application, call run
(run)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  #_(do
      (println "when does this happen")
      (swap! bs/app-state inc)))

