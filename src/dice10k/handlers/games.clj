(ns dice10k.handlers.games
  (:require [clj-uuid :as uuid]
            [clj-http.client :as http]
            [dice10k.domain
             [log :as log]
             [state :refer [state]]
             [resp :as resp]]))

(def dne "Game does not exist")

(defn random-words [n]
  (-> (http/get (str "https://random-word-api.herokuapp.com/word?number=" n)
                {:content-type :json})
      :body cheshire.core/parse-string))

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
      :always (assoc :turn-player (:name (whos-turn game-id))))))

;; Handlers

(def game-schema
  {:game-id :uuid
   :friendly-name :str
   :players :map-player-id-to-player
   :turn :int
   :turn-player :str
   :pending-points :int
   :pending-dice :int})

(defn new-game
  [{:keys [friendly-name] :as params}]
  (let [friendly-name (if (seq friendly-name)
                        friendly-name
                        (generate-name))
        game {:game-id (str (uuid/v4))
              :players {}
              :friendly-name friendly-name
              :turn 0
              :pending-points 0
              :pending-dice 0}]
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
  (swap! state assoc-in [(keyword game-id) :started] true)
  (let [{:keys [players]
         :as game} (get-game game-id)]
    (if (and game
             (seq players))
      (do (log/info "Starting Game!" game)
          (resp/success (assoc game :message "Game Started!")))
      (resp/fail {:message dne}))))

(def player-schema {:player-id :uuid
                    :turn-order :int
                    :name :str
                    :points :int
                    :turn-seq :int
                    :pending-points :int
                    :roll-vec :dice-vec
                    :ice-broken? :bool})

(defn add-player
  [game-id {:keys [name] :as params}]
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
  (let [players (:players (get-game game-id :safe false))]
    (if-let [player (some->> players
                             vals
                             (filter #(= player-id (:player-id %)))
                             first)]
      (resp/success player)
      (resp/fail {:message "Player does not exist"
                  :game-id game-id
                  :player-id player-id}))))
