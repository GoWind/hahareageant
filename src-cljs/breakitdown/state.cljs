(ns breakitdown.state
  (:require
    [clojure.spec.alpha :as spec]
    [reagent.core :as r]))

(defonce app-state (r/atom {:results {}}))


;;Task list is ultimately a map of things -> things
(def ::results (spec/map-of any? any?))

(spec/def ::breakitdown-state (spec/keys :req-un [::results]))

(defn edit-entry
  "set :edit for id to true. This implies user
   is currently editing entry at id"
  [state id]
  (assoc state :edit id))

(defn set-task-key
  [state task-id k value]
  (assoc-in state [:results task-id k] value))

(defn update-task-key
  [state task-id k f]
  (update-in state [:results task-id k] f))

(defn add-entry
  [state parent]
  ;;Guess a new id for a sub-item, by creating a random-int
  ;;and appending it to the id of the parent
  ;;silly, but works
  (let [results (:results state)
        new-id (str "step " parent "-" (rand-int 5000))]
    (if (not (contains? results new-id))
      (-> (assoc-in state [:results new-id] {:id new-id :text "Type something new" :parent parent})
          ;;set expand to true on the parent
          (set-task-key parent :expand true))
      (recur state parent))))



