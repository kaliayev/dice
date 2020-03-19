(ns dice10k.domain.resp)

(defn success
  [out]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body out})

(defn fail
  [out]
  {:status 400
   :headers {"Content-Type" "application/json"}
   :body out})

(defn plain
  [out]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body out})
