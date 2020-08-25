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
  {::uism/actors #{:actor/flagger
                   :actor/subject}

   ::uism/aliases {:active? [:actor/flagger :ui/active?]}

   ::uism/states {:initial {::uism/target-states #{:state/logged-in :state/logged-out}
                            ::uism/events        {::uism/started {::uism/handler (fn [env]
                                                                                   (-> env
                                                                                       (uism/assoc-aliased :active? true)
                                                                                       (uism/trigger (uism/asm-id env) :event/tick)))}
                                                  :event/tick    {::uism/handler (fn [env]
                                                                                   (-> env
                                                                                       (uism/load-actor :actor/subject)
                                                                                       (uism/set-timeout ::main-timer :event/tick {} 1000)
                                                                                       ))}
                                                  :event/exit    {::uism/handler (fn [env]
                                                                                   (-> env
                                                                                       (uism/assoc-aliased :active? false)
                                                                                       (uism/exit)))}}}}})

