(ns dice10k.core
  (:gen-class)
  (:require [compojure.core :refer [defroutes routes GET POST PUT DELETE context]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [dice10k.domain
             [log :as log]
             [state :as state]
             [resp :as resp]]
            [dice10k.handlers
             [actions :as actions]
             [games :as games]
             [stats :as stats]]))

(def port 3000)

(defroutes app-routes
  (context "/games" []
    (GET "/" {params :body} (state/dump-state params)) ;; admin dump, requires startup token
    (POST "/" {params :body} (games/new-game params)) ;; returns mgmt-token for managing participants

    (context "/:game-id" [game-id]
      (GET "/" [] (games/get-state game-id))
      (PUT "/start" [] (games/start game-id)) 
      (GET "/stats" [] (stats/game-stats game-id))

      (context "/players" []
        (POST "/" {params :body} (games/add-player game-id params)) ;; returns player-id token needed for playing

        (context "/:player-id" [player-id]
          (GET "/" [] (games/get-player game-id player-id))
          (DELETE "/" {params :body} (games/remove-player game-id player-id params)) ;; requires mgmt-token
          
          ;; Player Actions
          (POST "/roll" {params :body} (actions/roll game-id player-id params))
          (POST "/keep" {params :body} (actions/keep-dice game-id player-id params))
          (POST "/pass" [] (actions/pass game-id player-id))))))

  (resp/not-found {:message "Route not found: Either the id is invalid, your method is invalid or it's not even a real route. Who knows. Whatever you were looking for. It isn't here."}))

(def app
  (routes
   (-> app-routes
       (wrap-defaults api-defaults)
       (wrap-json-body {:keywords? true})
       wrap-json-response)))

(defn -main [& _]
  (log/start-log-worker)
  (log/info "Logger Started")
  (log/info (str "App started on port: " port) @state/state)
  (run-jetty app {:port port
                  :join? false}))
