(ns dice10k.handlers.games
  (:require [cheshire.core :refer [parse-string]]
            [clj-uuid :as uuid]
            [clj-http.client :as http]
            [dice10k.domain
             [log :as log]
             [state :refer [state] :as state]
             [resp :as resp]]))

(def dne "Game does not exist")

(defn random-words [n]
  (-> (http/get (str "https://random-word-api.herokuapp.com/word?number=" n)
                {:content-type :json})
      :body parse-string))

(defn- generate-name []
  (->> (random-words 2)
       (interpose " ")
       (apply str)))

(defn whos-turn [game-id]
  (let [{:keys [turn players]} ((keyword game-id) @state)]
    (when-let [players (seq (vals players))]
      (first (filter #(= (mod turn (count players)) (:turn-order %)) players)))))

(defn get-game [game-id & {:keys [safe] :or {safe true}}]
  (when-let [game ((keyword game-id) @state)]
    (cond-> game
      safe (update :players #(->> %
                                  (reduce-kv (fn [acc player-id player] (assoc acc (keyword player-id) (dissoc player :player-id))) {})
                                  vals))
      safe (dissoc :mgmt-token)
      :always (assoc :turn-player (:name (whos-turn game-id))))))

;; Handlers

(def game-schema
  {:game-id :uuid
   :friendly-name :str
   :players :map-player-id-to-player
   :turn :int
   :turn-player :str
   :pending-points :int
   :state :kw
   :pending-dice :int})

(defn new-game
  [{:keys [friendly-name]}]
  (let [friendly-name (if (seq friendly-name)
                        friendly-name
                        (generate-name))
        game {:game-id (str (uuid/v4))
              :players {}
              :friendly-name friendly-name
              :turn 0
              :state :created
              :pending-points 0
              :pending-dice 0
              :mgmt-token (str (uuid/v4))}]
    (log/info "Initiating new game" game)
    (swap! state assoc (-> game :game-id keyword) game)
    (resp/success game)))

(defn get-state
  [game-id]
  (if-let [game (get-game game-id)]
    (resp/success game)
    (resp/fail {:message dne})))

(defn start
  [game-id]
  (let [{:keys [players] :as game} (get-game game-id :safe false)
        error-msg (cond
                    (not game) dne
                    (not (seq players)) "Can't start a game with no players."
                    (not= (:state game) :created) "Game has already been started!")]
    (if error-msg
      (resp/fail {:message error-msg})
      (let [game (get-game game-id)]
        (swap! state assoc-in [(keyword game-id) :state] :started)
        (log/info "Starting Game!" game)
        (resp/success (assoc game :message "Game Started!"))))))

(def player-schema {:player-id :uuid
                    :turn-order :int
                    :name :str
                    :points :int
                    :turn-seq :int
                    :pending-points :int
                    :roll-vec :dice-vec
                    :ice-broken? :bool})

(defn add-player
  [game-id {:keys [name]}]
  (if-let [game (get-game game-id)]
    (let [player-name (if (seq name)
                        name
                        (generate-name))
          player {:player-id (str (uuid/v4)) ;; super-secret token
                  :name player-name
                  :turn-order (-> game :players keys count)
                  :points 0
                  :turn-seq 0
                  :pending-points 0
                  :ice-broken? false}]
      (swap! state assoc-in [(keyword game-id) :players (keyword (:player-id player))] player)
      (log/info "Added player" {:player player
                                :game (get-game game-id)})
      (resp/success {:message (format "You've been added, %s. Use your player-id for rolling/passing/keeping. Shhh, it's your secret token." (:name player))
                     :player-id (:player-id player)}))
    (resp/fail dne)))

(defn get-player [game-id player-id]
  (if-let [player (some->> game-id
                           (get-game :safe false)
                           (get-in [:players (keyword player-id)]))]
    (resp/success player)
    (resp/fail {:message "That player isn't a part of the provided game"
                :game-id game-id
                :player-id player-id})))

(defn remove-player [game-id player-id {:keys [mgmt-token]}]
  (let [{:keys [players] :as game} (get-game game-id :safe false)
        {:keys [turn-order] :as player} ((keyword player-id) players)
        precond-fail (cond
                       (not mgmt-token) "No mgmt-token has been provided to remove this player"
                       (not= mgmt-token
                             (:mgmt-token game)) "Incorrect mgmt-token provided for removing a player"
                       (not player) "That player isn't a part of the provided game")]
    (if precond-fail
      (resp/fail {:message precond-fail
                  :game-id game-id
                  :player-id player-id})
      (let [later-turn-player-ids (->> players
                                       vals
                                       (filter #(< turn-order (:turn-order %)))
                                       (map :player-id))]
        (swap! state update-in [(keyword game-id) :players] dissoc (keyword player-id))
        (doseq [pid later-turn-player-ids]
          (state/update-player-val game-id pid :turn-order dec))
        (resp/success {:message "Successfully removed player from game, turn orders adjusted accordingly."
                       :game (get-game game-id)})))))
