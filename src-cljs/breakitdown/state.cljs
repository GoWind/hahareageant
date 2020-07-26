(ns breakitdown.state
  (:require
    [clojure.edn :as edn]
    [ajax.core :refer [GET POST]]
    [reagent.core :as r]))

(defn empty-state []
  {:title ""
   :task-lists {}})

(defonce app-state (r/atom (empty-state)))


;; Special keys that require non-generic logic

(def task-list-id "task-list-title")
(def title-buffer-id :title-buffer)

(defn edit-entry
  "set :edit for id to true. This implies user
   is currently editing entry at id"
  [state id]
  (if (= id task-list-id)
    (assoc state :edit id :title-buffer (:selected-list state))
    (assoc state :edit id)))

(defn dissoc-edit-entry
  "the :edit entry is used to point to the current element (task title, task contents)
   that is being edit. When the user finished editing and presses a button or clicks outside of the 
   element being currently edit, `dissoc-edit-entry` is called to ensure that the element being edit's
   changes are saved"
  [state]
  (if (not= (:edit state) task-list-id)
    (dissoc state :edit)
    ;;special logic to handle editing title of task-list
    (let [{:keys [task-lists title-buffer selected-list]} state
          updated-title (if (not= title-buffer selected-list) title-buffer selected-list)
          updated-task-lists (if (not= title-buffer selected-list)
                               (clojure.set/rename-keys task-lists {selected-list title-buffer})
                               task-lists)]
      (-> state (assoc :task-lists updated-task-lists)
                (assoc :selected-list updated-title)
                (dissoc :edit :title-buffer)))))

(defn set-task-key
  "Set value of key k in the map for task with id `task-id` "
  [state task-id k value]
  (assoc-in state [:task-lists (:selected-list state) :tasks task-id k] value))

(defn update-task-key
  [state task-id k f]
  (update-in state [:task-lists (:selected-list state) :tasks task-id k] f))

(defn update-in-state
  [state k v]
  (assoc state k v))

(defn dissoc-in-state
  [state k]
  (dissoc state k))

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
  "remove task item with key id and its children, if any"
  [state id]
  (let [remaining-items  (filter (fn [[k v]] (not (or (= id k) (= id (:parent v)))))
                                 (get-in state [:task-lists (:selected-list state) :tasks]))]
    (assoc-in state [:task-lists (:selected-list state) :tasks] (into {}  remaining-items))))

(defn remove-task-list
  [state task-list-name]
  (update-in state [:task-lists] dissoc task-list-name))

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
(defn add-new-task-list
  [state]
  (let [{:keys [task-lists]} state
        new-title (str "New list" (rand-int 5000))]
    (if (not (contains? task-lists new-title))
      (-> state
          (assoc :selected-list new-title)
          (assoc-in [:task-lists new-title] {}))
      (recur state))))

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
  "Normalize m, that is an entry in the server's response into a Clojure-idiomatic
   map.

   m is a map of string keys, of which \"title\"
   is the title of the task-list and \"tasks\" is 
   a map of a string task id -> task
   
   Return a map with a :title key with the value of \"title\" in m
   and :tasks key with the value of \"tasks\" where each key in \"tasks\"
   is keywordized."
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
  "The server's response is expected to be a JSON encoded array (vector) of 
   maps. Normalize the response in a clojure-idiomatic way"
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
