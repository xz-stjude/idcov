(ns app.model.session
  (:require [app.application :refer [SPA]]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.ui-state-machines :as uism]
            [taoensso.timbre :as log]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [clojure.string :as str]
            [app.routing :as routing]))

(defn logout [env]
  (let [session-ident (uism/actor->ident env :actor/current-session)]
    (-> env
        ;; TODO: how can I clear the session data (instead of simply flagging the session "invalid"). Maybe read the manual to learn more about UI State Machines?

        ;; optimistically clear account data locally
        (uism/assoc-aliased :error ""
                            :username ""
                            :login-state :logged-out
                            :loading? false
                            :session-valid? false
                            :session-account nil)

        ;; (uism/apply-action assoc-in session-ident {})

        (uism/trigger-remote-mutation :actor/login-form 'app.model.session/logout {})

        ;; Remember, the above `trigger-remote-mutation` is async. Here the
        ;; :state/logged-out is activated immediately
        (uism/activate :state/logged-out))))

(defn login-by-email [{::uism/keys [event-data] :as env}]
  (-> env
      (uism/assoc-aliased :error ""
                          :login-state :logged-out
                          :loading? true)
      (uism/trigger-remote-mutation :actor/login-form 'app.model.session/login-by-email
                                    {:email             (:email event-data)
                                     :password          (:password event-data)
                                     ::m/returning      (uism/actor-class env :actor/current-session)
                                     ::uism/ok-event    :event/complete
                                     ::uism/error-event :event/failed})

      ;; Remember, the above `trigger-remote-mutation` is async. Here the
      ;; :state/checking-session is activated immediately
      (uism/activate :state/checking-session)))

(defn check-if-session-valid [env error-message]
  (if (uism/alias-value env :session-valid?)
    (do
      (routing/route-to! "/")
      (-> env
          (uism/assoc-aliased :error ""
                              :login-state :logged-in
                              :loading? false
                              :modal-open? false)
          (uism/activate :state/logged-in)))
    (-> env
        (uism/assoc-aliased :login-state :logged-out
                            :loading? false
                            :error error-message)
        (uism/activate :state/logged-out))))

(def global-events
  {:event/toggle-modal {::uism/handler (fn [env] (uism/update-aliased env :modal-open? not))}})

(uism/defstatemachine sm
  {::uism/actors #{;; mapped to root/Login
                   :actor/login-form
                   ;; mapped to root/Session
                   :actor/current-session}

   ::uism/aliases {:username        [:actor/login-form :account/email]
                   :error           [:actor/login-form :ui/error]
                   :modal-open?     [:actor/login-form :ui/open?]
                   :loading?        [:actor/login-form :ui/loading?]
                   :login-state     [:actor/login-form :ui/state] ;this login-state forwards the sm state into the login component
                   :session-valid?  [:actor/current-session :session/valid?]
                   :session-account [:actor/current-session :session/account]}

   ::uism/states {:initial                {::uism/target-states #{:state/logged-in :state/logged-out}
                                           ::uism/events        {::uism/started  {::uism/handler (fn [env]
                                                                                                   (routing/route-to! "/main")
                                                                                                   (-> env
                                                                                                       (uism/assoc-aliased :error ""
                                                                                                                           :loading? false
                                                                                                                           :login-state :initial)
                                                                                                       (uism/load ::current-session :actor/current-session
                                                                                                                  {::uism/ok-event    :event/complete
                                                                                                                   ::uism/error-event :event/failed})
                                                                                                       ))}
                                                                 :event/failed   {::uism/target-states #{:state/logged-out}
                                                                                  ::uism/handler       (fn [env]
                                                                                                         (-> env
                                                                                                             (uism/assoc-aliased :login-state :logged-out
                                                                                                                                 :loading? false)))}
                                                                 :event/complete {::uism/target-states #{:state/logged-in :state/logged-out}
                                                                                  ::uism/handler       #(check-if-session-valid % "")}}}
                  :state/checking-session {::uism/events (merge global-events
                                                                {:event/failed   {::uism/target-states #{:state/logged-out}
                                                                                  ::uism/handler       (fn [env]
                                                                                                         (-> env
                                                                                                             (uism/assoc-aliased :error "Server error.")))}
                                                                 :event/complete {::uism/target-states #{:state/logged-out :state/logged-in}
                                                                                  ::uism/handler       #(check-if-session-valid % "Invalid Credentials.")}})}
                  :state/logged-in        {::uism/events (merge global-events
                                                                {:event/logout {::uism/target-states #{:state/logged-out}
                                                                                ::uism/handler       logout}})}
                  :state/logged-out       {::uism/events (merge global-events
                                                                {:event/login-by-email {::uism/target-states #{:state/checking-session}
                                                                                        ::uism/handler       login-by-email}})}}})

(def signup-ident [:component/id :signup])
(defn signup-class [] (comp/registry-key->class :app.ui.root/Signup))

(defn clear-signup-form*
  "Mutation helper: Updates state map with a cleared signup form that is configured for form state support."
  [state-map]
  (-> state-map
      (assoc-in signup-ident
                {:account/email          ""
                 :account/password       ""
                 :account/password-again ""})
      (fs/add-form-config* (signup-class) signup-ident)))

(defmutation clear-signup-form [_]
  (action [{:keys [state]}]
          (swap! state clear-signup-form*)))

(defn valid-email? [email] (str/includes? email "@"))
(defn valid-password? [password] (> (count password) 7))

(defmutation signup! [_]
  (action [{:keys [state]}]
          (log/info "Marking complete")
          (swap! state fs/mark-complete* signup-ident))
  (ok-action [{:keys [app state]}]
             (routing/route-to! "/signup-success"))
  (remote [{:keys [state] :as env}]
          (let [{:account/keys [email password password-again]} (get-in @state signup-ident)]
            (boolean (and (valid-email? email) (valid-password? password)
                          (= password password-again))))))
