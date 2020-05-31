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

(defn task-tree?
  [task]
  (contains? task :subtree))

(defn render-leaf-task
  [task]
  (let [{:keys [id text]} task]
    ^{:key id} 
    [:li {:class "leaf-task"}
      [:input {:id id :type "checkbox"}]
      text]))

(defn render-task-tree
  [task]
  (let [{:strs [id subtree text expand checked]} task
        task (r/atom {:id      id
                      :subtree subtree
                      :text    text
                      :checked (or checked false)
                      :expand  false})
        tree? (not (empty? subtree))]
    (fn []
      [:li {:class "tasktree"}
       [:span {:on-click (fn [_]   (swap! task update :expand not))
               :class    (if tree? "expandablepointer" "")}  
        (if (:expand @task) "\u25bc" "\u25ba")]
       [:input {:id id 
                :checked checked
                :type "checkbox" 
                :on-change (fn [e] 
                             (swap! task assoc "checked" (.. e -target -checked))
                             (check-item id (.. e -target -checked)))}]
       [:span text] 
       (when (and (task-tree? @task) (:expand @task))
         (for [subtask (:subtree @task)]
           [render-task-tree (assoc subtask "checked" (:checked @task))]))])))

(defn render-task-list
  [state-atom]
  (let [tasks (:results @state-atom)]
    (println tasks)
    [:ul {:class "globaltasklist"}
     (for [task tasks]
       [render-task-tree task])]))
