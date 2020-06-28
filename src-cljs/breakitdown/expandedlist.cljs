(ns breakitdown.expandedlist
  (:require
   [ajax.core :refer [GET POST]]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<! take!]]
   [reagent.core :as r]
   [reagent.dom  :as rdom]
   
   [breakitdown.state :as state]))

(defn check-todo-item
  [id value]
  (let [error-handler (fn [e] (println e)) 
        resp          (POST "http://localhost:3449/check"
                            {:params {"id"    id
                                      "check" value}
                             :error-handler   error-handler})]))

(defn update-checked
  "When a user checks item `id`, check the item
   and all of the child items of `id`"
  [state id checked]
  ;;silly, but works
  (update state :results 
          (fn [r] 
            (into {}
                  ;;k is a todo item, v is the text and related info
                  (map (fn [[k v]]
                         (if (or (= k id) (= (:parent v) id))
                           [k (assoc v :checked checked)]
                           [k v]))
                       r)))))

(defn classes
  [& args]
  (clojure.string/join " " args))

(defn set-task-key
  [state task-id k value]
  (assoc-in state [:results task-id k] value))

(defn update-task-key
  [state task-id k f]
  (update-in state [:results task-id k] f))

(defn task-tree?
  [task]
  (not (empty? (:subtree task))))


(defn render-task-tree
  [task state-atom]
  (let [{:keys [id subtree text expand checked focus]} task
        edit  (:edit @state-atom)
        tree? (not (empty? subtree))]
      ^{:key id} 
      [:li {:class "tasktree"
            ;;stopPropagation ensure that the ancestor <li>'s mouse-over doesn't get activated as well
            :on-mouse-over  #(do (. % stopPropagation)
                                 (swap! state-atom set-task-key id :focus true))
            :on-mouse-out #(do   (. % stopPropagation)
                                 (swap! state-atom set-task-key id :focus false))}

       [:span {:on-click #(swap! state-atom update-task-key id :expand not)
               :class    (if tree? "pointer" "")}
        (if (:expand task) "\u25bc" "\u25ba")]

       [:input {:id id 
                :checked checked
                :type "checkbox" 
                :on-change (fn [e] 
                             (println "input checkbox")
                             (swap! state-atom update-checked id (.. e -target -checked))
                             (check-todo-item id (.. e -target -checked)))}]

       (if (= edit id)
         [:input {:value text
                  :on-change (fn [e] (swap! state-atom set-task-key id :text (.. e -target -value)))}]
         [:span {:class (if checked "strikethrough")} text])
        
       ;; When item is focused on show a "+" button, a "-" button and an "Edit" button
       ;; to the right to add sub-items, remove or edit current item
       
       (when focus [:span {:style {:margin-left "5px"
                                   :background "green"
                                   :color "white"
                                   :padding "2px"
                                   :font-weight "bold"}
                           :on-click (fn [e] (swap! state-atom state/add-entry id))} "+"])
       (when focus [:span {:style {:margin-left "5px"
                                   :background "red"
                                   :color "white"
                                   :padding "2px"
                                   :font-weight "bold"}
                           :on-click (fn [e] (swap! state-atom state/remove-entry id))} "-"])
       (when focus [:span {:style {:margin-left "5px"
                                   :font-weight "bold"}
                           :on-click (fn [e] (swap! state-atom state/edit-entry id))} "Edit"])
       (when (and (task-tree? task) expand)
         [:ul {:class "globaltasklist"}
          (for [subtask (:subtree task)]
            [render-task-tree subtask state-atom])])]))


(defn generate-tree
  "build the subtree of a parent into builder"
  [grouped-flat-tree builder parent]
  (when (some? parent)
    (let [entries (grouped-flat-tree parent)]
      (if (empty? entries)
        builder
        (into builder 
              (mapv (fn [entry]
                      {:id       (:id entry)
                       :text     (:text entry)
                       :expand   (:expand entry)
                       :checked  (:checked entry)
                       :focus    (:focus entry)
                       :subtree  (generate-tree grouped-flat-tree builder (:id entry))})  entries))))))


(defn render-task-list
  "This is the main fn that is called when the list is
   to be rendered"
  [state-atom]
  (fn []
    (let [grouped-tasks (group-by :parent
                                  (map  clojure.walk/keywordize-keys (vals (:results @state-atom))))
          tasks (generate-tree grouped-tasks [] "")
          edit  (:edit @state-atom)
          title (:title @state-atom)]
      [:div
       (if (= edit "title")
         [:input {:value title
                  :on-change (fn [e] 
                               (let [edited-title (.. e -target -value)]
                                 (swap! state-atom assoc :title (if (empty? edited-title) title edited-title))))}]
         [:h2 {:on-click #(swap! state-atom state/edit-entry "title")} (or title "New List")])
       [:button {:on-click #(swap! state-atom state/dump-to-storage)} "Save"]
       [:ul {:class "globaltasklist"}
        (for [task tasks]
          (render-task-tree task state-atom))
        [:li {:class (classes  "tasktree" "pointer")
              :on-click #(swap! state-atom state/add-entry "")} "+"]]])))
