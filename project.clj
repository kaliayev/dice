(defproject dice10k "0.2.0-SNAPSHOT"
  :description "classic dice 10000"
  :url "https://github.com/kaliayev/dice10k"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [compojure "1.6.1"]
                 [danlentz/clj-uuid "0.1.9"]
                 [cheshire "5.10.0"]
                 [clj-time "0.15.2"]
                 [clj-http "3.10.0"]
                 [camel-snake-kebab "0.4.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-core "1.8.0"]
                 [ring/ring-json "0.5.0"]
                 [ring/ring-jetty-adapter "1.8.0"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler dice10k.core/app}
  :main dice10k.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.4.0"]]}})
