(ns app.server-components.http-server
  (:require [app.server-components.config :refer [config]]
            [app.server-components.middleware :refer [middleware]]
            [mount.core :as mount :refer [defstate]]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.server :as http-kit]
            [taoensso.timbre :as log]
            [io.aviso.exception :as aviso-ex]))

(defstate http-server
  :start (let [cfg (::http-kit/config config)]
           (log/info "Starting HTTP Server with config " (with-out-str (pprint cfg)))
           (http-kit/run-server middleware (assoc cfg :error-logger (fn [s e]
                                                                      (log/error "Middleware reported error:" s)
                                                                      (log/error e)))))
  :stop (http-server))


(comment
  (mount/start #'http-server)

  )
