(ns app.routing
  (:require [app.application :refer [SPA]]
            [taoensso.timbre :as log]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [pushy.core :as pushy]
            [clojure.string :as s]))

(declare route-to!)

(defn path->route [p]
  (case p
    "/" (route-to! "/main")
    (dr/change-route SPA (-> p (s/split "/") rest vec))))

(defonce history (pushy/pushy path->route identity))

(defn start! []
  (pushy/start! history))

(defn route-to! [route-string]
  (pushy/set-token! history route-string))
