(ns app.server-components.config
  (:require
   [mount.core :as mount :refer [defstate]]
   [com.fulcrologic.fulcro.server.config :as fsc]
   [taoensso.encore :as enc]
   [taoensso.timbre :as log]
   [taoensso.timbre.appenders.core :as appenders]
   [clojure.string :as str]))

(defn simple-output-fn
  [data]
  (let [{:keys [level ?err msg_ ?ns-str ?file hostname_ timestamp_ ?line]} data]
    (str
      ;; (force timestamp_)
      ;; " "
      ;; (force hostname_)        " "
      (first (name level))  " "
      "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
      (force msg_)
      (when-let [err ?err]
        (str enc/system-newline (log/stacktrace err))))))

;; taoensso.timbre needs to configured effectfully
(defn configure-logging! [config-map]
  (let [{:keys [taoensso.timbre/logging-config]} config-map]
    (log/merge-config! logging-config)

    ;; persist the log into a file
    (log/merge-config! {:output-fn simple-output-fn
                        :appenders {:spit (appenders/spit-appender {:fname (:log-path config-map)})}})

    (log/info "Configured Timbre with" logging-config)))


(defstate config
  :start (let [{:keys [config-path defaults-path]
                :or   {config-path   "config/dev.edn"
                       defaults-path "config/defaults.edn"}} (mount/args)

               config-map (fsc/load-config! {:config-path   config-path
                                             :defaults-path defaults-path})]

           (log/info "Loaded config" config-map)
           (configure-logging! config-map)
           config-map))

(comment

  (log/debug "afdasfdf")
  )
