(ns breakitdown.state
  (:require
    [clojure.edn :as edn]
    [clojure.spec.alpha :as spec]
    [ajax.core :refer [GET POST]]
    [reagent.core :as r]))

(defn empty-state []
  {:results {}})

(defonce app-state (r/atom (empty-state)))


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
      (as-> (assoc-in state [:results new-id] {:id new-id :text "Type something new" :parent parent})
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
                                 (:results state))]
    (assoc state :results (into {}  remaining-items))))

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
    (.setItem ls "stored-lists" (:results state))
    state))

(defn normalize-server-resp
  "Given a map of {\"checklist item id\" -> checklist item},
   where checklist item is a map, keywordize all the keys,
   in checklist item"
  [m]
  (let [{:strs [title tasks]} m]
    {:title title
     :results (into {}
                    (map
                      (fn [[k v]]
                        [k (clojure.walk/keywordize-keys v)])
                      tasks))}))

(defn fetch-checklist
  [state-atom]
  (GET "http://localhost:3449/search"
       {:handler (fn
                   [v]
                   (swap! state-atom merge (normalize-server-resp v)))
        :error-handler (fn [e]
                         (reset! state-atom (load-from-session-storage)))}))
