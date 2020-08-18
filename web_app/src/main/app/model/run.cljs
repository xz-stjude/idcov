(ns app.model.run
  (:require
   [taoensso.timbre :as log]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
   [com.fulcrologic.fulcro.networking.http-remote :as hr]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [com.fulcrologic.fulcro.application :as app]))

(defmutation run-project [_]
  (remote [_] true))

(defmutation retract-run [_]
  (remote [_] true))

(defmutation stop-run [_]
  (remote [_] true))

(defmutation remove-run-from-account [_]
  (remote [_] true))
