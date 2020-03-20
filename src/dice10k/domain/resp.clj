(ns dice10k.domain.resp
  (:require [dice10k.domain.log :as log]))

(defn success
  [out]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body out})

(defn fail
  [out]
  (log/info (str "[400 Response] Bad Request: " (:message out)))
  {:status 400
   :headers {"Content-Type" "application/json"}
   :body out})

(defn plain
  [out]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body out})

(defn not-found
  [body]
  (fn handler
    [request]
    {:status 404
     :headers {"Content-Type" "application/json"}
     :body body}))


