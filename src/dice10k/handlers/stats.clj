(ns dice10k.handlers.stats
  (:require [dice10k.handlers.games :as games]
            [dice10k.domain.resp :as resp]))

(def stats [:keep-sums :scored-sums :num-dice-rolled :bust-count :pass-count :steal-count :attempted-steal-count])

(defn- prune-stats [{:keys [stats] :as player}]
  (let [{:keys [keep-sums num-dice-rolled]} stats
        keep-count (count keep-sums)
        roll-count (count num-dice-rolled)]
    (-> player
        (select-keys [:name :points :pending-points])
        (assoc :stats (-> stats
                          (select-keys [:bust-count :pass-count :steal-count])
                          (assoc :keep-count keep-count
                                 :avg-keep (/ (reduce + keep-sums) keep-count)
                                 :roll-count roll-count
                                 :avg-num-dice-rolled (/ (reduce + num-dice-rolled) roll-count)))))))
(defn game-stats [game-id]
  (let [players (-> game-id
                    games/get-game
                    :players
                    vals)]
    (->> players
         (map prune-stats)
         resp/success)))
