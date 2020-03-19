(ns dice10k.handlers.players
  (:require [dice10k.domain
             [dice :as dice]
             [log :as log]
             [resp :as resp]
             [scoring :as scoring]
             [state :refer [state]]]
            [dice10k.handlers.games :as games]))

(defn my-turn? [game-id player-id]
  (and (identity player-id)
       (= player-id
          (-> game-id games/whos-turn :player-id))))

(defn inc-turn-seq
  [game-id player-id]
  (swap! state update-in [(keyword game-id) :players (keyword player-id) :turn-seq] inc))

(defn reset-turn [game-id player-id]
  (swap! state assoc-in [(keyword game-id) :players (keyword player-id) :turn-seq] 0)
  (swap! state assoc-in [(keyword game-id) :players (keyword player-id) :roll-vec] []))

(defn bust-reset [game-id player-id]
  (reset-turn game-id player-id)
  (swap! state assoc-in [(keyword game-id) :players (keyword player-id) :pending-points] 0))

(defn score [game-id] ;; TODO: this uses stale data from the first 
  (when-let [player (first (filter #(not (zero? (:pending-points %))) (-> game-id games/get-game :players vals)))]
    (swap! state update-in [(keyword game-id) :players (keyword (:player-id player)) :points] + (:pending-points player))
    (swap! state assoc-in [(keyword game-id) :players (keyword (:player-id player)) :pending-points] 0)))

(defmulti update-game-state (fn [{:keys [type]}] type))
(defmethod update-game-state :roll
  [{:keys [game-id player-id roll-vec bust] :as params}]
  (log/info "Updating Game State on Roll" (assoc params :game (games/get-game game-id)))
  (if bust?
    (do (bust-reset game-id player-id)
        (score game-id))
    (do (inc-turn-seq game-id player-id)
        (swap! state assoc-in [(keyword game-id) :players (keyword player-id) :roll-vec] roll-vec))))

(defmethod update-game-state :keep ;; update player running points, game running points, update pending-dice, reset roll-vec
  [{:keys [pending-dice pending-points player-id game-id]}]
  (log/info "Updating Game State on keep" (games/get-game game-id))
  (inc-turn-seq game-id player-id)
  (swap! state assoc-in [(keyword game-id) :players (keyword player-id) :roll-vec] [])
  (swap! state assoc-in [(keyword game-id) :players (keyword player-id) :pending-points] pending-points)
  (swap! state assoc-in [(keyword game-id) :pending-points] pending-points)
  (swap! state assoc-in [(keyword game-id) :pending-dice] pending-dice)) ;; 
#_(defmethod update-game-state :pass) ;; reset player pending points other than current, reset roll-vec, score if ice-not-broken
#_(defmethod update-game-state :sass)

(defn roll [game-id player-id {:keys [steal]}]
  (if-let [game (games/get-game game-id :safe false)]
    (if (:started game)
      (if (my-turn? game-id player-id)
        (let [turn-seq (get-in game [:players (keyword player-id) :turn-seq])
              num-dice (if (or steal
                               (and (pos? turn-seq)
                                    (even? turn-seq)))
                         (:pending-dice game)
                         6) ;; TODO: in this case pending poitns are 0
              roll-result (dice/roll num-dice)
              bust? (scoring/bust? roll-result)
              msg (if bust? "You Busted!" "Pick Keepers or Pass?")]
          (update-game-state {:type :roll
                              :game-id game-id
                              :player-id player-id
                              :bust? bust? ;; TODO: on bust cleanup points
                              :roll-vec roll-result})
          (resp/success
           (-> game-id
               get-game
               (select-keys [:pending-points :pending-dice])
               (assoc :message msg
                      :roll roll-result))))
        (resp/fail {:message "It's not your turn, chill."}))
      (resp/fail {:message "The game hasn't started yet, chill."}))))

(defn keep [game-id player-id {:keys [keepers] :as params}]
  (if-let [game (games/get-game game-id :safe false)]
    (if (:started game)
      (if (my-turn? game-id player-id)
        (let [{:keys [pending-points players]} game
              {:keys [turn-seq roll-vec]} ((keyword player-id) players)]
          (if (odd? turn-seq)
            (let [_ (log/info "Keep step" {:roll-vec roll-vec
                                           :keepers keepers
                                           :partitioned (scoring/partition-keepers roll-vec keepers)})
                  partitioned-keepers (scoring/partition-keepers roll-vec keepers)
                  fail-message (cond
                                 (not partitioned-keepers) "Must pick at least one die"
                                 (scoring/bust? keepers) "Must pick at least one scoring die")]
              (if-not fail-message
                (let [{:keys [roll-points pending-dice]} (scoring/partition-points partitioned-keepers)
                      pending-points (+ roll-points pending-points)
                      game-update {:pending-dice pending-dice
                                   :pending-points pending-points
                                   :game-id game-id
                                   :player-id player-id}]
                  (update-game-state (assoc game-update :type :keep))
                  (resp/success (assoc game-update :message "Keepers are in. Roll or Pass?")))
                (resp/fail {:message fail-message})))
            (resp/fail {:message "It's not keeper time, it's roll or pass time"})))
        (resp/fail {:message "It's not your turn, chill."}))
      (resp/fail {:message "Game hasn't started yet, chill."}))))

(defn pass [game-id player-id]) ;; reset turn-seq

(defn sass [game-id player-id])
