(ns dice10k.domain.log
  (:require [clj-time.core :as time]
            [clojure.pprint :refer [pprint]])
  (:import [java.util.concurrent LinkedBlockingQueue]))

(def log-queue (LinkedBlockingQueue. 1024))

(defn start-log-worker []
  (future
    (loop []
      (let [data-str (.take ^LinkedBlockingQueue log-queue)]
        (println data-str))
      (recur))))

(defn info [message & [map]]
  (.put log-queue (str "[INFO] " message "\n" (with-out-str (pprint (assoc map :timestamp (str (time/now))))))))

(defn error [stack message & [map]]
  (.put log-queue (str "[ERROR] " message "\n" (with-out-str (pprint (assoc map :timestamp (str (time/now))))) "\n" (with-out-str (pprint stack)))))

