(ns app.model.project
  (:require
   [taoensso.timbre :as log]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
   [com.fulcrologic.fulcro.networking.http-remote :as hr]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [com.fulcrologic.fulcro.application :as app]))

(defmutation create-project-with-files [_]
  ;; (remote [env] (m/returning env root/AccountProjectList))
  (action
    [{:keys [app]}]
    (df/set-load-marker! app ::create-project-with-files :loading))
  (progress-action
    [{:keys [state] :as env}]
    (log/spy env)
    (swap! state #(-> %
                      ;; phase is one of #{:sending :receiving :complete :failed}
                      (assoc-in [df/marker-table ::create-project-with-files :progress-phase] (get-in env [:progress :raw-progress :progress-phase]))
                      (assoc-in [df/marker-table ::create-project-with-files :send-progress] (hr/send-progress env))
                      (assoc-in [df/marker-table ::create-project-with-files :receive-progress] (hr/receive-progress env))
                      (assoc-in [df/marker-table ::create-project-with-files :overall-progress] (hr/overall-progress env)))))
  (ok-action
    [{:keys [app]}]
    (df/set-load-marker! app ::create-project-with-files :complete))
  (error-action
    [{:keys [app]}]
    (df/set-load-marker! app ::create-project-with-files :error))
  (remote [_] true))

(defmutation remove-project [{:keys [project-id account-id]}]
  ;; TODO: the immdiate client-side action should be darkening + loading spinner but not disappearing
  ;; (action [{:keys [state]}]
  ;;         (swap! state
  ;;                merge/remove-ident* [:project/id project-id] [:account/id account-id :account/projects]))
  (remote [_] true))
