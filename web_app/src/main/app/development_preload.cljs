(ns app.development-preload
  (:require
   [com.fulcrologic.fulcro.algorithms.timbre-support :as ts]
   [taoensso.timbre :as log]
   [taoensso.timbre.appenders.core :as appenders]))

(log/set-level! :debug)
(log/merge-config! {:output-fn    ts/prefix-output-fn
                    :appenders    {:console (ts/console-appender)}
                    :ns-blacklist ["com.fulcrologic.fulcro.algorithms.indexing"
                                   "com.fulcrologic.fulcro.algorithms.tx-processing"
                                   "com.fulcrologic.fulcro.data-fetch"
                                   "com.fulcrologic.fulcro.inspect.inspect-client"
                                   "com.fulcrologic.fulcro.routing.dynamic-routing"
                                   "com.fulcrologic.fulcro.ui-state-machines"]})
