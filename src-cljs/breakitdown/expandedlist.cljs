(ns breakitdown.expandedlist
  (:require
   [clojure.edn :refer [read-string]]
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
  "Generate a link to download the contents of the application's state
   and remove the `download attribute from the state"
  [state-atom filename attribute]
  [:a#right {:href (str  "data:text/plain;charset=UTF-8," (str (dissoc  @state-atom attribute)))
       :on-click #(swap! state-atom dissoc attribute)
       :download filename} "click here"])

(defn restore-data-from-file
  [state-atom uploadevent attribute]
  (let [file (aget  (.. uploadevent -target -files ) 0)
        file-reader (js/FileReader.)
        on-read (fn [e] 
                  (try (let [new-state (->  (read-string (. file-reader -result)))]
                                  (reset! state-atom (dissoc new-state attribute)))
                                (catch js/Error e
                                  (do (println e) (js/alert "Unable to restore data")))))
        on-error (fn [e] (do (println e) (js/alert "Unable to restore data")))]
    (set! file-reader -onloadend on-read)
    (set! file-reader -onerror on-error)
    (. file-reader readAsText file))) 

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
  "Show a list of the titles of task-lists currently stored.
   Each title is a link, which, when clicked, will select the task-list
   with the `title` to be displayed in a different pane"
  [state-atom]
  (let [state                @state-atom
        {:keys [task-focused task-lists selected-list]} state]
    [:div
     [:h3 "Task Lists"]
     [:button {:on-click #(swap! state-atom state/add-new-task-list)} "New"]
     [:ul
        (for [task-list-name (keys task-lists)]
            ^{:key task-list-name}
            [:li {
                  ;;stopPropagation ensure that the ancestor <li>'s mouse-over doesn't get activated as well
                  :on-mouse-over #(do (. % stopPropagation)
                                      (swap! state-atom state/update-in-state :task-focused task-list-name))
                  :on-mouse-out #(do (. % stopPropagation)
                                     (swap! state-atom state/dissoc-in-state :task-focused))} 
             [:a {:href task-list-name
                  :class (if (= selected-list task-list-name) "title_highlight" "")
                  :on-click (fn [e] 
                              (.preventDefault e)
                              (swap! state-atom state/update-in-state :selected-list task-list-name))} 
              task-list-name]
             (when (= task-focused task-list-name) 
               [:span {:class (classes "addbutton" "pointer" "removebutton")
                       :on-click (fn [e] 
                                   (let [confirmed (js/confirm "Delete list ?")]
                                     (if confirmed
                                       (swap! state-atom state/remove-task-list task-list-name))))} 
                      "Remove"])])]]))

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
          title (:selected-list @state-atom)
          title-buffer (:title-buffer @state-atom)
          download (:download @state-atom)
          choose-file (:choose-file @state-atom)]
      [:div#app_box
       [:h2#center "Break it Down - The app"]

       [:div#menu_bar
        (if choose-file
          [:input#right {:class "choosefile"
                         :type "file"
                         :accept "*.edn"
                         :text "Restore from file"
                         :on-change (fn [e] (restore-data-from-file state-atom e :choose-file))}]
          [:button#right {:on-click #(swap! state-atom state/update-in-state :choose-file true)} "Restore from file"])
        (if download
          (download-data state-atom (str (or title "New List") ".edn") :download)
          [:button#right {:on-click #(swap! state-atom state/update-in-state :download true)} "Download"])
        [:button#right {:on-click #(swap! state-atom state/dump-to-storage)} "Save"]]

       [:div#flex_container

        [:div#task_lists_panel
         [show-tasklists state-atom]]

        [:div#task_list_view
         (if (= edit state/task-list-id)
           [:input {:value title-buffer
                    :on-change (fn [e] 
                                 (let [edited-title (.. e -target -value)]
                                   (swap! state-atom assoc state/title-buffer-id (if (empty? edited-title) title edited-title))))}]
           [:h2#task_list_title
            {:class (classes "pointer")
             :on-click #(swap! state-atom state/edit-entry "task-list-title")} (or title "New List")])
         [:br]



         [:ul {:class "globaltasklist"}
          (for [task tasks]
            (render-task-tree task state-atom))
          [:button {:class (classes  "tasktree" "pointer" "greenbutton")
                    :on-click #(swap! state-atom state/add-entry "")} "+ New Item"]]]]])))
