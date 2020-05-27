(ns haha-server.server)


(defn handler [request]
  (println request)
  {:status 200
   :body "okhariputterji"})
