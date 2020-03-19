(ns dice10k.handler
  (:gen-class)
  (:require [compojure.core :refer [defroutes routes GET POST PUT context]]
            [compojure.route :as route]
            [ring.util.response :refer []]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [clj-uuid :as uuid]
            [dice10k.domain
             [log :as log]
             [state :as state]
             [resp :as resp]]
            [dice10k.handlers
             [games :as games]
             [players :as players]]))

(defroutes app-routes
  (context "/games" []
    (GET "/" [] (resp/success @state/state)) ;; admin dump
    (POST "/" {params :body} (games/new params)) ;; New Game

    (context "/:game-id" [game-id]
      (GET "/" [] (games/get-state game-id)) ;; Game state
      (PUT "/start" [] (games/start game-id))
      

      (context "/players" []
        (POST "/" {params :body} (games/add-player game-id params)) ;; Add new player

        (context "/:player-id" [player-id]
          (GET "/" [] (games/get-player game-id player-id))
          
          ;; Player Actions
          (POST "/roll" {params :body} (players/roll game-id player-id params))
          (POST "/keep" {params :body} (players/keep game-id player-id params))
          (POST "/pass" [] (players/pass game-id player-id))
          (POST "/sass" {params :body} (players/sass game-id player-id params))))))

  (route/not-found {:message "Not a real route..."}))

(def app
  (routes
   (-> app-routes
       (wrap-defaults api-defaults)
       (wrap-json-body {:keywords? true})
       wrap-json-response)))

(defn -main [& args]
  (log/start-log-worker)
  (log/info "Logger Started")
  (run-jetty app {:port 3000
                  :join? false}))
