(ns breakitdown-server.server
  (:require
   [cheshire.core :as json]
   [ring.middleware.reload :refer [wrap-reload]]))

(defn return-something
  []
  (json/generate-string
   (take (rand-int 5)
         (repeatedly rand))))

(defn rpan
  []
  {:title "List 1"
   :tasks {"step 1" {:id "step 1" :text "do something everyday" :parent ""}
           "step 2" {:id "step 2" :text "do not accept defeat" :parent ""}
           "step 3"{:id "step 3" :text "Make small incremental changes" :parent ""}
           "step 4"{:id "step 4" :text "tactics" :parent ""}
           "4a" {:id "4a" :parent "step 4" :text "get into something fulltime"}
           "4b" {:id "4b" :parent "step 4" :text "block all other distractions"}
           "4c" {:id "4c" :parent "step 4" :text "Get rid of facebook"}
           "4d" {:id "4d" :parent "step 4" :text "Ignore naysayers"}
           "4k" {:id "4k" :parent "step 4" :text "Build. Shipx"}
           "step 5"{:id "step 5" :text "screwit"}}})

(defn handler* [request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string  (rpan))})


(def handler (wrap-reload #'handler*))
