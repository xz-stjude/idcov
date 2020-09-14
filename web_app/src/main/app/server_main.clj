(ns app.server-main
  (:require [mount.core :as mount]
            app.server-components.http-server
            app.jrd.jrd)
  (:gen-class))

;; This is a separate file for the uberjar only. We control the server in dev mode from src/dev/user.clj
(defn -main [& args]
  (mount/start))



