(ns breakitdown.components)

(defn compound-component
  []
  [:p
   [simple-component @app-state]
   [:button {:on-click #(swap! app-state inc)} "Click to increment"]
   [:br]
   [:button {:on-click #(reset! app-state 0)} "Click to Reset"]])

(defn tree-view
  [something]
  [:ul
   (for [s something]
          (if (coll? s)
            [:li (tree-view s)]
            [:li s]))])

(defn simple-component [prop]
  [:div
   [:p "I am a component and I have been reloaded " (str  prop) " time"]])

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
