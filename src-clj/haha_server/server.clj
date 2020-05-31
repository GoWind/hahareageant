(ns haha-server.server
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
  [{:id "step 1" :text "do something everyday"}
   {:id "step 2" :text "do not accept defeat"}
   {:id "step 3" :text "Make small incremental changes"}
   {:id "step 4" :text "tactics" :subtree [{:id "4a" :text "get into something fulltime"}
                                           {:id "4b" :text "block all other distractions"}
                                           {:id "4c" :text "Get rid of facebook"}
                                           {:id "4d" :text "Ignore naysayers"}
                                           {:id "4k" :text "Build. Shipx"}]}
   {:id "step 5" :text "screwit"}])

(defn handler* [request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string  (rpan))})


(def handler (wrap-reload #'handler*))
