(ns app.model.auto-refresh
  (:require [app.application :refer [SPA]]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.ui-state-machines :as uism]
            [taoensso.timbre :as log]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [app.routing :as routing]
            [com.fulcrologic.fulcro.data-fetch :as df]))

(uism/defstatemachine sm
  {::uism/actors #{}

   ::uism/aliases {}

   ::uism/states {:initial       {::uism/events {::uism/started {::uism/handler (fn [env]
                                                                                  (-> env
                                                                                      (uism/activate :state/stopped)))}}}
                  :state/stopped {::uism/events {:event/start {::uism/handler (fn [{::uism/keys [event-data] :as env}]
                                                                                (log/spy event-data)
                                                                                (-> env
                                                                                    (uism/store :tick-fn (:tick-fn event-data))
                                                                                    (uism/trigger (uism/asm-id env) :event/tick)
                                                                                    (uism/activate :state/running)))}}}
                  :state/running {::uism/events {:event/stop {::uism/handler (fn [env]
                                                                               (-> env
                                                                                   (uism/activate :state/stopped)))}
                                                 :event/tick {::uism/handler (fn [env]
                                                                               (log/debug "tick!")
                                                                               (log/spy (uism/retrieve env :tick-fn))
                                                                               ((uism/retrieve env :tick-fn))
                                                                               ;; (log/debug "Done!")
                                                                               (-> env
                                                                                   (uism/set-timeout :main-timer :event/tick {} 1000)))}}}}})

