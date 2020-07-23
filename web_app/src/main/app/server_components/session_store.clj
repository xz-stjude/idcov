(ns app.server-components.session-store
  (:require [mount.core :refer [defstate args]]
            [ring.middleware.session.store :refer [SessionStore]])
  (:import [java.util UUID]))

(def store-atom (atom {}))

;; TODO: use something persistent to the disk, so that the sessions won't get lost when server restarts

;; copied from ring.middleware.session.memory
(deftype MemoryStore [session-map]
  SessionStore
  (read-session [_ key]
    (@session-map key))
  (write-session [_ key data]
    (let [key (or key (str (UUID/randomUUID)))]
      (swap! session-map assoc key data)
      key))
  (delete-session [_ key]
    (swap! session-map dissoc key)
    nil))

;; copied from ring.middleware.session.memory
(defn memory-store
  ([] (memory-store (atom {})))
  ([session-atom] (MemoryStore. session-atom)))

(defstate session-store
  :start (memory-store store-atom))
