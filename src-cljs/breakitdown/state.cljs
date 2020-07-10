(ns breakitdown.state
  (:require
    [clojure.edn :as edn]
    [ajax.core :refer [GET POST]]
    [reagent.core :as r]))

(defn empty-state []
  {:title ""
   :task-lists {}})

(defonce app-state (r/atom (empty-state)))

(defn edit-entry
  "set :edit for id to true. This implies user
   is currently editing entry at id"
  [state id]
  (assoc state :edit id))

(defn set-task-key
  [state task-id k value]
  (assoc-in state [:task-lists (:selected-list state) :tasks task-id k] value))

(defn update-task-key
  [state task-id k f]
  (update-in state [:task-lists (:selected-list state) :tasks task-id k] f))

(defn add-entry
  [state parent]
  ;;Guess a new id for a sub-item, by creating a random-int
  ;;and appending it to the id of the parent
  ;;silly, but works
  (let [
        selected-list (:selected-list state)
        tasks (get-in state [:task-lists selected-list :tasks] state)
        new-id (str "step " parent "-" (rand-int 5000))]
    (if (not (contains? tasks new-id))
      (as-> (assoc-in state [:task-lists selected-list :tasks new-id] {:id new-id :text "Type something new" :parent parent})
            updated-state
            ;;If parent id is nil, do not set :expand to true
            ;;as it causes a stack overflow error
            ;;TODO: refactor this nicely :)
            (if (some? parent)
              (set-task-key updated-state parent :expand true)
              updated-state))
      (recur state parent))))

(defn remove-entry
  "remove item with key id and its children, if any"
  [state id]
  (let [remaining-items  (filter (fn [[k v]] (not (or (= id k) (= id (:parent v)))))
                                 (get-in state [:task-lists (:selected-list state) :tasks]))]
    (assoc-in state [:task-lists (:selected-list state) :tasks] (into {}  remaining-items))))

(defn update-checked
  "When a user checks item `id`, check the item
   and all of the child items of `id`"
  [state id checked]
  ;;silly, but works
  (update-in state [:task-lists (:selected-list state) :tasks]
          (fn [r]
            (into {}
                  ;;k is a todo item, v is the text and related info
                  (map (fn [[k v]]
                         (if (or (= k id) (= (:parent v) id))
                           [k (assoc v :checked checked)]
                           [k v]))
                       r)))))

(defn local-storage?
  []
  (let [local-storage (. js/window -localStorage)]
    (try
      (.setItem  local-storage "a" "b")
      (.removeItem  local-storage "a")
      true
     (catch js/Exception e
      (and (instance? js/DOMException e)
           (or (= (. e -code) 22)
               (= (. e -code) 1014)
               (= (. e -name) "QuotaExceededError")
               (= (. e -name) "NS_ERROR_DOM_QUOTA_REACHED"))
           (and local-storage (not= (. local-storage -length) 0)))))))

(defn load-from-session-storage
  []
  (if (local-storage?)
    (let [ls (. js/window -localStorage)]
      (or
        (edn/read-string  (.getItem ls "stored-lists"))
        (empty-state)))))

(defn dump-to-storage
  [state]
  (let [ls (. js/window -localStorage)]
    (.setItem ls "stored-lists" (:task-lists state))
    state))

(defn normalize-server-task-list
  "Given a map of {\"checklist item id\" -> checklist item},
   where checklist item is a map,
   keywordize all the keys in checklist item  
   and return a vector of [title map]"
  [m]
  (let [{:strs [title tasks]} m]
    [title
     {:title      title
      :tasks (into {}
                        (map
                          (fn [[k v]]
                            [k (clojure.walk/keywordize-keys v)])
                          tasks))}]))

(defn normalize-server-resp
  [vec-of-maps]
  (let [task-lists (into {} (map normalize-server-task-list vec-of-maps))]
    {:selected-list (nth (first task-lists) 0)
     :task-lists task-lists}))

(defn current-tasklist
  "Return the current \"selected\" tasklist,
   given state"
  [state]
  (let [task-key (:selected-list state)]
    (get-in state [:task-lists task-key])))

(defn fetch-checklist
  [state-atom]
  (GET "http://localhost:3449/search"
       {:handler (fn
                   [v]
                   (reset! state-atom (normalize-server-resp v)))
        :error-handler (fn [e]
                         (reset! state-atom (load-from-session-storage)))}))
