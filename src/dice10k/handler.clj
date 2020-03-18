(ns dice10k.handler
  (:gen-class)
  (:require [compojure.core :refer [defroutes GET POST context]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.adapter.jetty :refer [run-jetty]]
            [clj-uuid :as uuid]
            [dice10k.log :as log]))

(defroutes app-routes
  (context "/games" []
           (GET "/games" [] "Hello World")
           
           (POST "/games/:game-id" [] ) ;; New Game
           (GET "/games/:game-id" [] "Hello World")
           (context "/players" [] ;; Game state

                    (POST "/games/:game-id/players" [] "Hello World") ;; Add new player
                    (GET "/games/:game-id/players/:player-id" [] "Hello World") ;; get player-game state

                    ;; Actions
                    (GET "/games/:game-id/players/:player-id/roll" [] "Hello World")
                    (GET "/games/:game-id/players/:player-id/pass" [] "Hello World")
                    (GET "/games/:game-id/players/:player-id/sass" [] "Hello World"))) 

  (route/not-found "Not a real route..."))

(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      ))

(defn -main [& args]
  (log/start-log-worker)
  (log/info "Logger Started")
  (run-jetty app {:port 3000
                  :join? true}))
