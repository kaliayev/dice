(ns dice10k.domain.scoring
  (:require [dice10k.domain.rules :as rules]))

(defn exp2 [x n] (* x (reduce * (take n (repeat 2))))) ;; lol

;; special cases

(defn three-pair? [partitions]
  (= 3 (count (filter #(= 2 (count %)) partitions))))

(defn straight? [partitions]
  (= rules/num-dice (count partitions)))

;; Regular scoring

(defn scoring-multiples [[n :as part]]
  (let [coefficient (if (= 1 n)
                      1000
                      (* 100 n))]
    (exp2 coefficient (- (count part) 3))))

(defn ones-fives [[n :as part]]
  (case n
    1 (* (count part) 100)
    5 (* (count part) 50)
    0))

(defn partition-reducer
  [{:keys [pending-dice roll-points]} part]
  (let [part-points (cond
                      (<= 3 (count part)) (scoring-multiples part)
                      (ones-fives part) (ones-fives part)
                      :else 0)]
    {:pending-dice (- pending-dice (if (pos? part-points) (count part) 0))
     :roll-points (+ roll-points part-points)}))

(defn eval-points
  [dice-vec]
  (let [partitions (->> dice-vec
                        (group-by identity)
                        vals
                        (into []))]
    (cond
      (straight? partitions) {:pending-dice 0
                              :roll-points 3000}
      (three-pair? partitions) {:pending-dice 0
                                :roll-points 1500}
      :else (reduce partition-reducer
                    {:pending-dice (count dice-vec)
                     :roll-points 0}
                    partitions))))

(defn bust? [dice-vec]
  (-> dice-vec eval-points :roll-points zero?))

(defn- drop-first
  [l n]
  (cond
    (= n (first l)) (rest l)
    (first l) (cons (first l) (drop-first (rest l) n))
    :else l))
(defn drop-when
  [l n]
  (let [shorter-list (drop-first l n)]
    (if (= (count l)
           (count shorter-list))
      false
      shorter-list)))
(defn partition-keepers
  [roll-vec keep-vec]
  (let [partitioned-keepers (reduce (fn [{:keys [unscored-dice] :as partitioned} keeper]
                                      (if-let [unscored-dice (drop-when unscored-dice keeper)]
                                        (-> partitioned
                                            (assoc :unscored-dice unscored-dice)
                                            (update :scoring-dice conj keeper))
                                        (reduced false)))
                                    {:scoring-dice []
                                     :unscored-dice roll-vec}
                                    keep-vec)]
    (when (seq (:scoring-dice partitioned-keepers))
      partitioned-keepers)))

(defn partition-points
  [{:keys [scoring-dice unscored-dice] :as partitioned-keepers}]
  (let [{:keys [roll-points pending-dice]} (eval-points scoring-dice)]
    {:roll-points roll-points
     :pending-dice (+ pending-dice (count unscored-dice))}))
