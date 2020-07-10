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
  (let [error-handler (fn [e])
        resp          (POST "http://localhost:3449/check"
                            {:params {"id"    id
                                      "check" value}
                             :error-handler   error-handler})]))


(defn classes
  [& args]
  (clojure.string/join " " args))


(defn task-tree?
  [task]
  (not (empty? (:subtree task))))

(defn download-data
  [state-atom filename attribute]
  [:a {:href (str  "data:text/plain;charset=UTF-8," (str (:task-lists @state-atom)))
       :on-click #(swap! state-atom dissoc attribute)
       :download filename} "click here"])

(defn render-task-tree
  [task state-atom]
  (let [{:keys [id subtree text expand checked focus]} task
        edit  (:edit @state-atom)
        tree? (not (empty? subtree))]
      ^{:key id} 
      [:li {:class "tasktree"
            ;;stopPropagation ensure that the ancestor <li>'s mouse-over doesn't get activated as well
            :on-mouse-over  #(do (. % stopPropagation)
                                 (swap! state-atom state/set-task-key id :focus true))
            :on-mouse-out #(do   (. % stopPropagation)
                                 (swap! state-atom state/set-task-key id :focus false))}

       [:span {:on-click #(swap! state-atom state/update-task-key id :expand not)
               :class    (if tree? "pointer" "")}
        (if (:expand task) "\u25bc" "\u25ba")]

       [:input {:id id 
                :checked checked
                :type "checkbox" 
                :on-change (fn [e] 
                             (swap! state-atom state/update-checked id (.. e -target -checked))
                             (check-todo-item id (.. e -target -checked)))}]

       (if (= edit id)
         [:input {:value text
                  :on-change (fn [e] (swap! state-atom state/set-task-key id :text (.. e -target -value)))}]
         [:span {:class (if checked "strikethrough")} text])
        
       ;; When item is focused on show a "+" button, a "-" button and an "Edit" button
       ;; to the right to add sub-items, remove or edit current item
       
       (when focus [:span {:class (classes "addbutton" "pointer")
                           :on-click (fn [e] (swap! state-atom state/add-entry id))} "+"])
       (when focus [:span {:class (classes "removebutton" "pointer") 
                           :on-click (fn [e] (swap! state-atom state/remove-entry id))} "-"])
       (when focus [:span {:class (classes "editbutton" "pointer") 
                           :on-click (fn [e] (swap! state-atom state/edit-entry id))} "Edit"])
       (when (and (task-tree? task) expand)
         [:ul {:class "globaltasklist"}
          (for [subtask (:subtree task)]
            [render-task-tree subtask state-atom])])]))

(defn show-tasklists
  [state-atom]
  (let [state                @state-atom
        {:keys [task-lists]} state]
    [:div
     [:h3 "Task Lists"]
     [:ul
        (for [task-list-name (keys task-lists)]
          ^{:key task-list-name}
          [:li 
           [:a {:href task-list-name
                :on-click (fn [e] (.preventDefault e)
                            (swap! state-atom 
                                   assoc :selected-list task-list-name))} task-list-name]])]]))

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


(defn render-application
  "This is the main fn that is called when the list is
   to be rendered"
  [state-atom]
  (fn []
    (let [task-list     (state/current-tasklist @state-atom)
          grouped-tasks (group-by :parent
                                  (map clojure.walk/keywordize-keys (vals (:tasks task-list))))
          tasks (generate-tree grouped-tasks [] "")
          edit  (:edit @state-atom)
          title (:title @state-atom)
          download (:download @state-atom)]
      [:div#flex_container

       [:div#task_lists_panel
        [show-tasklists state-atom]]

       [:div#task_list_view
        (if (= edit "task-list-title")
          [:input {:value title
                   :on-change (fn [e] 
                                (let [edited-title (.. e -target -value)]
                                  (swap! state-atom assoc :title (if (empty? edited-title) title edited-title))))}]
          [:h2#task_list_title
           {:class (classes "pointer")
            :on-click #(swap! state-atom state/edit-entry "task-list-title")} (or title "New List")])
        [:br]

        [:button {:on-click #(swap! state-atom state/dump-to-storage)} "Save"]
        [:button {:on-click #(swap! state-atom assoc :download true)} "Download"]

        (when download
          (download-data state-atom (str (or title "New List") ".edn") :download))

        [:ul {:class "globaltasklist"}
         (for [task tasks]
           (render-task-tree task state-atom))
         [:button {:class (classes  "tasktree" "pointer" "greenbutton")
                   :on-click #(swap! state-atom state/add-entry "")} "+ New Item"]]]])))
