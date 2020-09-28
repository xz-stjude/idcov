(ns app.server-components.session-store
  (:require [mount.core :refer [defstate args]]
            [ring.middleware.session.memory :as rsm]))

(def store-atom (atom {}))

;; TODO: use something persistent to the disk, so that the sessions won't get lost when server restarts

(defstate session-store
  :start (rsm/memory-store store-atom))
