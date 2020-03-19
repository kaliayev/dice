(ns dice10k.domain.dice)

(def six-template
  "+---+---+---+
| %s | %s | %s |
+---+---+---+
| %s | %s | %s |
+---+---+---+")

(def five-template
  "+---+---+---+
| %s | %s | %s |
+---+---+---+
| %s | %s |
+---+---+")

(def four-template
  "+---+---+---+
| %s | %s | %s |
+---+---+---+
| %s |
+---+")

(def three-template
  "+---+---+---+
| %s | %s | %s |
+---+---+---+")

(def two-template
  "+---+---+
| %s | %s |
+---+---+")

(def one-template
  "+---+
| %s |
+---+")

(defn render
  [dice-vec]
  (let [template (case (count dice-vec)
                   6 six-template
                   5 five-template
                   4 four-template
                   3 three-template
                   2 two-template
                   1 one-template)]
    (apply format template dice-vec)))

(defn roll-single
  []
  (inc (rand-int 6)))

(defn roll [n]
  (->> (repeatedly roll-single)
       (take n)
       (into [])))
