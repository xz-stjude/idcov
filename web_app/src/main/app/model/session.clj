(ns app.model.session
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [com.fulcrologic.fulcro.server.api-middleware :as fmw]
            [com.fulcrologic.guardrails.core :refer [=> >defn ? |]]
            [com.wsscode.pathom.connect :as pc :refer [defmutation defresolver]]
            [datahike.api :as d]
            [taoensso.timbre :as log]
            [app.server-components.config :as config]
            [app.model.file :as file]
            [clojure.java.io :as io]))

;; TODO: Change the name of the resolvers

(defresolver current-session
  ;; Determine whether the session is logged-in based on whether the ring-session is valid.
  ;;
  ;; By default, all sessions are stored on the server-side as an atom using ring.middleware.session.memory/memory-store.
  ;; A request to this resolver should come with it a UUID in its cookie (under the field \"session\"). The biggest significance
  ;; of a valid session is its association with an account. The mass majority of the things presented to the client
  ;; are with respect to this account. In a way, this account is the peephole through which the client looks at the whole database."
  [{{{{id :account/id} :session/account
      valid?           :session/valid?} :session} :ring/request} _input]
  {::pc/output [{::current-session [:session/valid?
                                    {:session/account [:account/id]}]}]}
  (if valid?
    {::current-session {:session/valid? true :session/account {:account/id id}}}
    {::current-session {:session/valid? false}}))

(defn response-updating-session
  "Uses `mutation-response` as the actual return value for a mutation, but also stores the data into the (cookie-based) session."
  [mutation-env mutation-response]
  (let [existing-session (some-> mutation-env :ring/request :session)]
    (fmw/augment-response
      mutation-response
      (fn [resp]
        (let [new-session (merge existing-session mutation-response)]
          (assoc resp :session new-session))))))

(defmutation login-by-email [{:keys [conn] :as env} {:keys [email password]}]
  {::pc/output [:session/valid?
                {:session/account [:account/id]}]}
  (log/info "Authenticating" email)
  (let [[id expected-email expected-password]
        (d/q '[:find [?id ?email ?password]
               :in $ ?email
               :where
               [?e :account/id ?id]
               [?e :account/email ?email]
               [?e :account/password ?password]]
             @conn email)]
    (log/debug (format "expected-email = %s, expected-password = %s" expected-email expected-password))
    (if (and (= email expected-email) (= password expected-password))
      (response-updating-session env {:session/valid? true, :session/account {:account/id id}})
      (do
        (log/error "Invalid credentials supplied for" email)
        (throw (ex-info "Invalid credentials" {:email email}))))))

(defmutation logout [env params]
  {::pc/output [:session/valid?]}
  (response-updating-session env {:session/valid? false}))

(defmutation signup! [{:keys [conn]} {:keys [email password]}]
  {::pc/output [:signup/result]}
  (let [{:keys [example-project-path]} config/config]
    (if (nil? example-project-path)

      (d/transact conn [{:account/id       (java.util.UUID/randomUUID)
                         :account/email    email
                         :account/password password}])

      (let [files              (.listFiles (io/file example-project-path))
            files-tx           (mapv file/link-file files)
            example-project-tx {:project/id    (java.util.UUID/randomUUID)
                                :project/name  "Example project"
                                :project/files files-tx}]
        (d/transact conn [{:account/id       (java.util.UUID/randomUUID)
                           :account/email    email
                           :account/password password
                           :account/projects [example-project-tx]}]))))
  ;; Add an example project to every new user
  {:signup/result "OK"})

(def resolvers [current-session login-by-email logout signup!])


