(ns haha.expandedlist
  (:require
   [ajax.core :refer [GET POST]]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<! take!]]
   [reagent.core :as r]
   [reagent.dom  :as rdom]))

(defn check-item
  [id value]
  (let [error-handler (fn [e] (println e)) 
        resp          (POST "http://localhost:3449/check"
                            {:params {"id"    id
                                      "check" value}
                             :error-handler   error-handler})]))

(defn update-checked
  [state id checked]
  ;;silly, but works
  (update state :results 
          (fn [r] 
            (into {}
                  (map (fn [[k v]]
                         (if (or (= k id) (= (:parent v) id))
                           [k (assoc v :checked checked)]
                           [k v]))
                       r)))))
(defn add-entry
  [state parent]
  ;;TODO: change rand-int into something different
  (let [new-id (str "step " parent "-" (rand-int 5000))]
    (assoc-in state [:results new-id] {:id new-id :text "Type something new" :parent parent})))

(defn set-task-key
  [state task-id k value]
  (assoc-in state [:results task-id k] value))

(defn update-task-key
  [state task-id k f]
  (update-in state [:results task-id k] f))

(defn task-tree?
  [task]
  (not (empty? (:subtree task))))

(defn render-leaf-task
  [task]
  (let [{:keys [id text]} task]
    ^{:key id} 
    [:li {:class "leaf-task"}
      [:input {:id id :type "checkbox"}]
      text]))

(defn render-task-tree
  [task state-atom]
  (let [{:keys [id subtree text expand checked focus]} task
        tree? (not (empty? subtree))]
    (println "focus is " (keys task))
      ^{:key id} 
      [:li {:class "tasktree"
            :on-mouse-over  #(swap! state-atom assoc-in  [:results id :focus] true)
            :on-mouse-leave #(swap! state-atom assoc-in  [:results id :focus] false)}

       [:span {:on-click #(swap! state-atom update-task-key id :expand not)
               :class    (if tree? "expandablepointer" "")
               :on-hover (fn [e] (println e))}  
        (if (:expand task) "\u25bc" "\u25ba")]

       [:input {:id id 
                :checked checked
                :type "checkbox" 
                :on-change (fn [e] 
                             (swap! state-atom update-checked id (.. e -target -checked))
                             (check-item id (.. e -target -checked)))}]

       [:span text] 

       (when (and focus expand) [:span {:style {:margin-left "5px"
                                   :font-weight "bold"}
                           :on-click (fn [e] (swap! state-atom add-entry id))} "+"])
       (when (and (task-tree? task) expand)
         (for [subtask (:subtree task)]
           [render-task-tree (assoc subtask :checked (:checked task)) state-atom]))]))





(defn generate-tree
  [grouped-flat-tree builder parent]
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
                     :subtree  (generate-tree grouped-flat-tree builder (:id entry))})  entries)))))


(defn render-task-list
  [state-atom]
  (fn []
    (let [grouped-tasks (group-by :parent
                                  (map  clojure.walk/keywordize-keys (vals (:results @state-atom))))
          tasks (generate-tree grouped-tasks [] nil)]
      [:ul {:class "globaltasklist"}
       (for [task tasks]
         (render-task-tree task state-atom))]
      )))
