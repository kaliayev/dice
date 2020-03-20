(ns dice10k.domain.state
  (:require [clj-uuid :as uuid]
            [dice10k.domain
             [resp :as resp]
             [rules :as rules]]))

(defonce state (atom {:app-token (str (uuid/v4))}))

(defn dump-state
  [{app-token :app-token}]
  (if (= (:app-token @state) app-token)
    (resp/success @state)
    {:status 401
     :headers {"Content-Type" "application/json"}
     :body {:message "Failed Auth: app-token didn't match for this instance."}}))

(defn ten-thousand? [game-id]
  (let [players (-> @state
                    (get-in [(keyword game-id) :players])
                    vals)]
    (->> players
         (map :points)
         (filter #(= % rules/winning-score))
         first)))

(defn update-player-val [game-id player-id kw upd]
  (let [modifier (if (fn? upd) update-in assoc-in)]
    (swap! state modifier [(keyword game-id) :players (keyword player-id) kw] upd)))

(defn update-game-val [game-id kw upd]
  (let [modifier (if (fn? upd) update-in assoc-in)]
    (swap! state modifier [(keyword game-id) kw] upd)))

(defn update-stat-val [game-id player-id stat-kw fn]
  (swap! state update-in [(keyword game-id) :players (keyword player-id) :stats stat-kw] fn))

(defn update-turn-seq
  [game-id player-id upd]
  (update-player-val game-id player-id :turn-seq upd))

(defn update-roll-vec [game-id player-id roll-vec]
  (update-player-val game-id player-id :roll-vec roll-vec))

(defn pass-reset [game-id player-id]
  (update-turn-seq game-id player-id 0)
  (update-roll-vec game-id player-id [])
  (update-game-val game-id :turn inc))

(defn update-pending-player-points [game-id player-id upd]
  (update-player-val game-id player-id :pending-points upd))

(defn bust-reset [game-id player-id]
  (pass-reset game-id player-id)
  (update-pending-player-points game-id player-id 0))

(defn reset-game-dice-and-points [game-id]
  (update-game-val game-id :pending-points 0)
  (update-game-val game-id :pending-dice rules/num-dice))

(defn score-flush [game-id] ;; TODO: this uses stale data from the first fetch
  (reset-game-dice-and-points game-id)
  (when-let [{:keys [pending-points player-id]
              :as player} (first (filter #(not (zero? (:pending-points %))) (-> ((keyword game-id) @state) :players vals)))]
    (update-player-val game-id player-id :points #(+ pending-points %))
    (update-pending-player-points game-id player-id 0)
    (when (ten-thousand? game-id)
      (update-game-val game-id :state :completed))))

(defn reset-pending-points-all [game-id]
  (swap! state assoc-in [(keyword game-id) :players]
         (reduce-kv (fn [acc player-id player]
                      (assoc acc (keyword player-id) (assoc player :pending-points 0)))
                    {}
                    (get-in @state [(keyword game-id) :players]))))
