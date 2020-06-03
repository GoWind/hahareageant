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
  (let [{:keys [id subtree text expand checked]} task
        tree? (not (empty? subtree))]
      ^{:key id} 
      [:li {:class "tasktree"}
       [:span {:on-click #(swap! state-atom update-in [:results id :expand] not)
               :class    (if tree? "expandablepointer" "")}  
        (if (:expand task) "\u25bc" "\u25ba")]
       [:input {:id id 
                :checked checked
                :type "checkbox" 
                :on-change (fn [e] 
                             (swap! state-atom update-checked id (.. e -target -checked))
                             (check-item id (.. e -target -checked)))}]
       [:span text] 
       (when (and (task-tree? task) (:expand task))
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
