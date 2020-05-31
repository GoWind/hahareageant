(ns haha.components)

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
