(ns dice10k.handlers.actions
  (:require [dice10k.domain
             [dice :as dice]
             [log :as log]
             [resp :as resp]
             [scoring :as scoring]
             [state :as state]]
            [dice10k.handlers.games :as games]))

(defn my-turn? [game-id player-id]
  (and (identity player-id)
       (= player-id
          (-> game-id games/whos-turn :player-id))))

(defn action-precond-fail-msg [game-id player-id]
  (let [game (games/get-game game-id :safe false)]
    (cond
      (nil? game) "This game doesn't exist"
      (= :created
         (:state game)) "The game hasn't started yet, chill."
      (= :completed
         (:state game)) "Game is over! Someone already won."
      (not (my-turn? game-id player-id)) "It's not your turn, chill.")))

(defmulti update-game-state (fn [{:keys [type]}] type))

;;;;;;;;;;;;;
;; Roll Logic
;;
;; Preconditions:
;; 1. Ice-broken? -> if-not, previous player scores immediately, game-pending-points/dice reset
;; 2. Steal -> only-if ice-broken?
;; Cases
;; 1. Bust -> if not ice-broken? score already set. score previous points. Resetting previous scores done on :pass.
;; 2. Not Bust -> inc turn seq, update roll-vec, update game-pending-dice
;;;;;;;;;;;;;
(defmethod update-game-state :pre-roll
  [{:keys [game-id player-id steal ice-broken? turn-seq] :as params}]
  (when-not (or ice-broken? steal (pos? turn-seq))
    (state/score-flush game-id)
    (log/info "Updated Game State pre-roll: score-flush" (assoc params :game (games/get-game game-id)))))
(defmethod update-game-state :roll
  [{:keys [game-id player-id roll-vec bust?] :as params}]
  (if bust?
    (do (state/bust-reset game-id player-id)
        (state/score-flush game-id)
        (log/info "Updated Game State post-roll: bust" (assoc params :game (games/get-game game-id))))
    (do (state/update-turn-seq game-id player-id inc)
        (state/update-roll-vec game-id player-id roll-vec)
        (state/update-game-val game-id :pending-dice (- 6 (count roll-vec)))
        (log/info "Updated Game State post-roll: no bust" (assoc params :game (games/get-game game-id))))))

(defn roll-precond-fail-msg [game-id player-id steal]
  (let [game (games/get-game game-id :safe false)
        player (get-in game [:players (keyword player-id)])
        turn-message (action-precond-fail-msg game-id player-id)]
    (cond
      turn-message turn-message
      (odd? (:turn-seq player)) "You can't roll at keeper-pickin' time."
      (and steal
           (< 10000 (+ (:points player)
                       (:pending-points game)))) "You can't steal, it'll put you over 10k."
      :else (do (update-game-state {:type :pre-roll
                                    :player-id player-id
                                    :game-id game-id
                                    :ice-broken? (:ice-broken player)
                                    :turn-seq (:turn-seq player)
                                    :steal steal})
                nil))))

(defn roll [game-id player-id {:keys [steal] :as params}]
  (log/info "Received Roll" {:game-id game-id
                             :player-id player-id
                             :params params
                             :game (games/get-game game-id :safe false)})
  (if-let [msg (roll-precond-fail-msg game-id player-id steal)]
    (resp/fail {:message msg})
    (let [{:keys [pending-dice]} (games/get-game game-id)
          roll-result (dice/roll pending-dice)
          bust? (scoring/bust? roll-result)
          msg (if bust? "You Busted!" "Pick Keepers!")]
      (update-game-state {:type :roll
                          :game-id game-id
                          :player-id player-id
                          :bust? bust?
                          :roll-vec roll-result})
      (log/info "Successful Roll: updated state" {:game-id game-id
                                                  :player-id player-id
                                                  :params params
                                                  :game (games/get-game game-id :safe false)})
      (resp/success
       (assoc {:message msg
               :roll roll-result
               :game-state (games/get-game game-id)})))))

;;;;;;;;;;;;;
;; Keep Logic
;;
;; Preconditions:
;; 1. turn-seq even
;; 2. non-empty keepers
;; 3. all keepers taken from roll-vec
;;
;; Cases:
;; 1. Normal -> reset roll-vec, bump player-pending-points, bump game-pending-points/dice, inc turn-seq
;;;;;;;;;;;;;
(defmethod update-game-state :keep ;; update player running points, game running points, update pending-dice, reset roll-vec
  [{:keys [pending-dice pending-points player-id game-id] :as params}]
  (state/update-turn-seq game-id player-id inc)
  (state/update-roll-vec game-id player-id [])
  (state/update-pending-player-points game-id player-id pending-points)
  (state/update-game-val game-id :pending-points pending-points)
  (state/update-game-val game-id :pending-dice pending-dice)
  (log/info "Updated Game State post-keep" (assoc params :game (games/get-game game-id))))


(defn keep-precond-fail-msg [game-id player-id keepers]
  (let [player (-> game-id
                   (games/get-game :safe false)
                   (get-in [:players (keyword player-id)]))
        turn-message (action-precond-fail-msg game-id player-id)]
    (cond
      turn-message turn-message
      (not (seq keepers)) "You need at least one die value under 'keepers' param"
      (even? (:turn-seq player)) "It's not keeper-pickin' time, either pass or roll")))

(defn keep [game-id player-id {:keys [keepers] :as params}]
  (log/info "Received Keep" {:game-id game-id
                             :player-id player-id
                             :params params
                             :game (games/get-game game-id :safe false)})
  (if-let [msg (keep-precond-fail-msg game-id player-id keepers)]
    (resp/fail {:message msg})
    (let [{:keys [pending-points players]} (games/get-game game-id :safe false)
          {:keys [turn-seq roll-vec name]} ((keyword player-id) players)
          partitioned-keepers (scoring/partition-keepers roll-vec keepers)
          fail-message (cond
                         (not partitioned-keepers) "Must pick at least one die"
                         (scoring/bust? keepers) "Must pick at least one scoring die")]
      (if fail-message
        (resp/fail {:message fail-message})
        (let [{:keys [roll-points pending-dice]} (scoring/partition-points partitioned-keepers)
              pending-points (+ roll-points pending-points)]
          (if (> pending-points 10000)
            (resp/fail (assoc (games/get-game game-id)
                              :message (format "I can't let you do that, %s. It would put you over, at %s points"
                                               name pending-points)))
            (do (update-game-state {:type :keep
                                    :pending-dice (if (zero? pending-dice) 6 pending-dice)
                                    :pending-points pending-points
                                    :game-id game-id
                                    :player-id player-id})
                (log/info "Keep Success: updated game" {:game-id game-id
                                                        :player-id player-id
                                                        :params params
                                                        :game (games/get-game game-id :safe false)})
                (resp/success {:message "Keepers are in. Roll or Pass?"
                               :roll-points roll-points
                               :pending-points pending-points
                               :dice-remaining pending-dice
                               :game-state (games/get-game game-id)}))))))))

;;;;;;;;;;;;;
;; Pass Logic
;;
;; Preconditions:
;; 1. turn-seq pos? and even?
;; 2. ice-broken? true or player-pending-score > 1000
;;
;; Cases
;; 1. Normal -> reset turn-seq, reset all pending-player-points, reset roll-vec, bump game-turn, set player-pending-points, pending-game-dice-update, pending-game-points update
;; 2. Breaking the ice -> reset turn-seq, reset all pending-player-points, reset roll-vec, bump game-turn, score player-pending points, reset pending-game-dice, reset pending-game-points
;;
;;;;;;;;;;;;;

(defn- ice-breaker? [ice-broken? pending-points]
  (and (not ice-broken?)
       (<= 1000 pending-points)))
(defmethod update-game-state :pass
  [{:keys [game-id player-id ice-broken? pending-points] :as params}]
  (state/pass-reset game-id player-id)
  (state/reset-pending-points-all game-id)
  (if (ice-breaker? ice-broken? pending-points)
    (do (state/update-player-val game-id player-id :points pending-points)
        (state/update-player-val game-id player-id :ice-broken? true)
        (state/reset-game-dice-and-points game-id)
        (log/info "Updated Game State post-pass: breaking ice" (assoc params :game (games/get-game game-id))))
    (do (state/update-pending-player-points game-id player-id pending-points)
        (state/update-game-val game-id :pending-points pending-points)
        (log/info "Updated Game State post-pass: normal case" (assoc params :game (games/get-game game-id))))))

(defn pass-precond-fail-msg [game-id player-id]
  (let [{:keys [ice-broken? pending-points turn-seq points]
         :as player} (-> game-id
                         (games/get-game :safe false)
                         (get-in [:players (keyword player-id)]))
        turn-message (action-precond-fail-msg game-id player-id)]
    (cond
      turn-message turn-message
      (not (or (ice-breaker? ice-broken? pending-points)
               (>= points 1000))) "Ice, ice, baby... Gotta break it to pass."
      (odd? turn-seq) "You can't pass at keeper-pickin' time."
      (zero? turn-seq) "Can't pass on your first turn. Roll the dice, baby.")))

(defn pass [game-id player-id]
  (log/info "Received Pass" {:game-id game-id
                             :player-id player-id
                             :game (games/get-game game-id :safe false)})
  (if-let [msg (pass-precond-fail-msg game-id player-id)]
    (resp/fail {:message msg})
    (let [{:keys [pending-points ice-broken?]
           :as player} (-> game-id
                           (games/get-game :safe false)
                           (get-in [:players (keyword player-id)]))]
      (update-game-state {:type :pass
                          :game-id game-id
                          :player-id player-id
                          :ice-broken? ice-broken?
                          :pending-points pending-points})
      (log/info "Successful Pass: Updated game" {:game-id game-id
                                                 :player-id player-id
                                                 :game (games/get-game game-id :safe false)})
      (resp/success {:message "Successful pass."
                     :pending-points pending-points
                     :game-state (games/get-game game-id)}))))
