(ns app.ui.root
  (:require
   [app.model.project :as project]
   [app.model.session :as session]
   [app.model.run :as run]
   [app.routing :as routing]
   [clojure.string :as str]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro-css.css :as css]
   [com.fulcrologic.fulcro.dom :as dom :refer [a b i img button div h2 h3 input label p span form code pre]]
   [com.fulcrologic.fulcro.dom.events :as evt]
   [com.fulcrologic.fulcro.dom.html-entities :as ent]
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.fulcro.networking.file-upload :as fu]
   [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
   [com.fulcrologic.fulcro.ui-state-machines :as uism]
   [taoensso.timbre :as log]
   ["filesize" :as filesize]

   [com.fulcrologic.fulcro.data-fetch :as df]))

(declare
  SessionAccount ui-session-account
  AccountUploadNewProject ui-account-upload-new-project
  File ui-file
  Login
  Main
  MainSessionView ui-main-session-view
  ProjectItem ui-project-item
  Root
  Session
  Settings
  Signup
  SignupSuccess
  TopChrome ui-top-chrome
  RunItem ui-run-item)



(defsc File [this {:file/keys [id name size]}]
  {:ident         :file/id
   :query         [:file/id :file/name :file/size]
   :initial-state {}}
  (div :.item
       (i :.large.icon.file)
       (div :.content
            (div :.header (str name " (" (filesize size) ")"))
            (div :.description (str id))))
  )

(def ui-file (comp/factory File {:keyfn :file/id}))


(defsc ProjectItem [this
                    {:project/keys [id name files] :as props}
                    {:keys [remove-project run-project]}]
  {:ident         :project/id
   :query         [:project/id :project/name
                   {:project/files (comp/get-query File)}]
   :initial-state {}}
  (div :.item
       (i :.large.icon.edit)
       (div :.content
            (div :.header
                 name
                 " - " (a {:onClick #(remove-project id)} "remove")
                 " - " (a {:onClick #(run-project id)} "run"))
            (div :.description (str id))
            (div :.list
                 (map ui-file files)))
       ))

(def ui-project-item (comp/computed-factory ProjectItem {:keyfn :project/id}))


(defsc RunItem [this
                {:run/keys [id name status message]}
                {:keys [retract-run remove-run]}]
  {:ident         :run/id
   :query         [:run/id :run/name :run/status :run/message]
   :initial-state {}
   :css           [[:.retracted {:text-decoration "line-through"
                                 :opacity         0.3}]
                   [:.failed {:color "red"}]
                   [:.succeeded {:color "green"}]]}
  (let [classes     (css/get-classnames RunItem)
        retracted-c (:retracted classes)
        succeeded-c (:succeeded classes)
        failed-c    (:failed classes)]
    (div :.item
         {:classes (filter some? [(when (= :retracted status) retracted-c)
                                  (when (= :failed status) failed-c)
                                  (when (= :succeeded status) succeeded-c)])}
         (i :.large.icon.plane)
         (div :.content
              (div :.header
                   name
                   " " (str status)
                   (when (= :initiated status)
                     (comp/fragment " - " (a {:onClick #(retract-run id)} "retract")))
                   (when (contains? #{:retracted :initiation-failed :succeeded :failed} status)
                     (comp/fragment " - " (a {:onClick #(remove-run id)} "remove")))
                   (when (= :succeeded)
                     (comp/fragment " - " (a {:href (str "/run/" id "/results.tar.gz") :data-pushy-ignore true} "download results")))
                   ;; " - " (a {:onClick #(run-project id)} "run")
                   )
              (when (seq message) (pre :.description (div :.ui.segment (str message))))
              (div :.description (str id))))))

(def ui-run-item (comp/computed-factory RunItem {:keyfn :run/id}))


(defsc SessionAccount [this {:account/keys [projects runs]}]
  {:ident          :account/id
   :query          [:account/id
                    :account/email
                    {:account/projects (comp/get-query ProjectItem)}
                    {:account/runs (comp/get-query RunItem)}]
   :initial-state  {}
   :initLocalState (fn [this props]
                     {:refresh        (fn [] (df/load! this [:account/id (:account/id (comp/props this))] SessionAccount))
                      :retract-run    (fn [run-id]
                                        (comp/transact! this [{(run/retract-run
                                                                 {:run-id run-id})
                                                               (comp/get-query RunItem)}]))
                      :remove-run     (fn [run-id]
                                        (comp/transact! this [{(run/remove-run-from-account
                                                                 {:run-id     run-id
                                                                  :account-id (:account/id (comp/props this))})
                                                               (comp/get-query SessionAccount)}]))
                      :remove-project (fn [project-id]
                                        (comp/transact! this [{(project/remove-project
                                                                 {:project-id project-id
                                                                  :account-id (:account/id (comp/props this))})
                                                               (comp/get-query SessionAccount)}]))
                      :run-project    (fn [project-id]
                                        (comp/transact! this [{(run/run-project
                                                                 {:project-id project-id
                                                                  :account-id (:account/id (comp/props this))})
                                                               (comp/get-query SessionAccount)}]))})}
  (let [active? (some #(contains? #{:initiating :initiated :running} (:run/status %)) (log/spy runs))]
    (div
      (button :.ui.compact.labeled.icon.button
              {:onClick (comp/get-state this :refresh)}
              (i :.refresh.icon)
              "Refresh")
      (div :.ui.segment
           (h3 :.ui.header "Runs"
               (when (log/spy active?) (span :.sub.header {:style {:display "inline"}} " active ...")))
           (div :.ui.relaxed.divided.list {}
                (for [run runs]
                  (ui-run-item run (select-keys (comp/get-state this) [:retract-run :remove-run])))))
      (div :.ui.segment
           (h3 :.ui.header "Projects")
           (div :.ui.relaxed.divided.list {}
                (for [project projects]
                  (ui-project-item project (select-keys (comp/get-state this) [:remove-project :run-project]))))))))

(def ui-session-account (comp/factory SessionAccount))


(defn field [{:keys [label valid? error-message] :as props}]
  (let [input-props (-> props (assoc :name label) (dissoc :label :valid? :error-message))]
    (div :.ui.field
         (dom/label {:htmlFor label} label)
         (input input-props)
         (div :.ui.error.message {:classes [(when valid? "hidden")]}
              error-message))))

(defsc SignupSuccess [this props]
  {:query         ['*]
   :initial-state {}
   :ident         (fn [] [:component/id :signup-success])
   :route-segment ["signup-success"]}
  (div
    (h3 "Signup Complete!")
    (p "You can now log in!")))

(defsc Signup [this {:account/keys [email password password-again] :as props}]
  {:query             [:account/email :account/password :account/password-again fs/form-config-join]
   :initial-state     (fn [_]
                        (fs/add-form-config Signup
                                            {:account/email          ""
                                             :account/password       ""
                                             :account/password-again ""}))
   :form-fields       #{:account/email :account/password :account/password-again}
   :ident             (fn [] session/signup-ident)
   :route-segment     ["signup"]
   :componentDidMount (fn [this]
                        (comp/transact! this [(session/clear-signup-form)]))}
  (let [submit!  (fn [evt]
                   (when (or (identical? true evt) (evt/enter-key? evt))
                     (comp/transact! this [(session/signup! {:email email :password password})])
                     (log/info "Sign up")))
        checked? (fs/checked? props)]
    (div
      (h3 "Signup")
      (form :.ui.form {:classes [(when checked? "error")]}
            (field {:label         "Email"
                    :value         (or email "")
                    :valid?        (session/valid-email? email)
                    :error-message "Must be an email address"
                    :autoComplete  "off"
                    :onKeyDown     submit!
                    :onChange      #(m/set-string! this :account/email :event %)})
            (field {:label         "Password"
                    :type          "password"
                    :value         (or password "")
                    :valid?        (session/valid-password? password)
                    :error-message "Password must be at least 8 characters."
                    :onKeyDown     submit!
                    :autoComplete  "off"
                    :onChange      #(m/set-string! this :account/password :event %)})
            (field {:label         "Repeat Password" :type "password" :value (or password-again "")
                    :autoComplete  "off"
                    :valid?        (= password password-again)
                    :error-message "Passwords do not match."
                    :onChange      #(m/set-string! [this] :account/password-again :event %)})
            (button :.ui.primary.button {:type    "button"
                                         :onClick #(submit! true)}
                    "Sign Up")))))

(defsc Login [this {:account/keys [email]
                    :ui/keys      [error open?]
                    :as           props}]
  {:ident (fn [] [:component/id :login])
   :query [:ui/open?
           :ui/error

           :account/email

           {[:component/id :session] (comp/get-query Session)}

           ;; The below does not send in anything but declares that if the data under this ident
           ;; changes, then the component should be re-rendered.
           [::uism/asm-id ::session/session]]
   :initial-state {:ui/error      ""
                   :account/email ""}
   :css           [[:.floating-menu {:position "absolute !important"
                                     :z-index  1000
                                     :width    "300px"
                                     :right    "0px"
                                     :top      "50px"}]]}
  ;; (print "props =" props)
  (let [;; Note that this "current-state" is not sent in as a prop
        current-state           (uism/get-active-state this ::session/session)
        session                 (get props [:component/id :session])
        account                 (:session/account session)
        account-id              (:account/id account)
        account-email           (:account/email account)
        {:keys [floating-menu]} (css/get-classnames Login)
        password                (or (comp/get-state this :password) "")] ; c.l. state for security
    (div
      (div :.right.menu
           (case current-state
             (nil :initial)            (span :.item "Initializing ...")
             :state/logged-in          (button :.item
                                               {:onClick #(uism/trigger! this ::session/session :event/logout)}
                                               (span :.ui.image.label (img {:src "/avataaars.svg"}) (str account-email)) ent/nbsp "Log out")
             (:state/logged-out
              :state/checking-session) (div :.item {:style   {:position "relative"}
                                                    :onClick #(uism/trigger! this ::session/session :event/toggle-modal)}
                                            "Login"
                                            (when open?
                                              (div :.four.wide.ui.raised.teal.segment {:onClick (fn [e]
                                                                                                  ;; Stop bubbling (would trigger the menu toggle)
                                                                                                  (evt/stop-propagation! e))
                                                                                       :classes [floating-menu]}
                                                   (h3 :.ui.header "Login")
                                                   (form :.ui.form {:classes [(when (seq error) "error")]}
                                                         (field {:label    "Email"
                                                                 :value    email
                                                                 :onChange #(m/set-string! this :account/email :event %)})
                                                         (field {:label    "Password"
                                                                 :type     "password"
                                                                 :value    password
                                                                 :onChange #(comp/set-state! this {:password (evt/target-value %)})})
                                                         (div :.ui.error.message error)
                                                         (div :.ui.field
                                                              (button :.ui.button
                                                                      {:type    "button"
                                                                       :onClick (fn [] (uism/trigger! this ::session/session :event/login-by-email
                                                                                                      {:email    email
                                                                                                       :password password}))
                                                                       :classes [(when (= :state/checking-session current-state) "loading")]} "Login"))
                                                         (div :.ui.message
                                                              (p "Don't have an account?")
                                                              (a {:onClick (fn []
                                                                             (uism/trigger! this ::session/session :event/toggle-modal {})
                                                                             (routing/route-to! "/signup"))}
                                                                 "Please sign up!")))))))))))

(def ui-login (comp/factory Login))

(defsc AccountUploadNewProject [this {:ui/keys [new-project-name] :as props}]
  {:ident         (fn [] [:component/id :upload-new-project-panel])
   :query         [{[:component/id :session] [{:session/account [:account/id]}]}
                   :ui/new-project-name
                   [df/marker-table '_]]
   :initial-state {:ui/new-project-name ""}}
  (let [marker           (get-in (log/spy props) [df/marker-table :app.model.project/create-project-with-files])
        status           (:status marker)
        progress-phase   (:progress-phase marker)
        overall-progress (:overall-progress marker)
        send-progress    (:send-progress marker)
        receive-progress (:receive-progress marker)]
    (div :.ui.segment
         (form :.ui.form
               (div :.field
                    (label "Project Name")
                    (input {:value    (or new-project-name "")
                            :onChange (fn [evt] (m/set-string! this :ui/new-project-name :event evt))}))
               (div :.field
                    (label "FastQ Files")
                    (input {:type     "file"
                            :multiple true
                            :onChange (fn [evt]
                                        (let [files (fu/evt->uploads evt)]
                                          (comp/set-state! this {:fastq-files files})))}))
               (button {:type    "button"
                        :onClick (fn []
                                   (let [fastq-files (comp/get-state this :fastq-files)]
                                     (comp/transact!
                                       this
                                       [{(log/spy (project/create-project-with-files
                                                    (fu/attach-uploads
                                                      {:project-name new-project-name
                                                       :account-id   (get-in props [[:component/id :session] :session/account :account/id])}
                                                      fastq-files)))
                                         (comp/get-query SessionAccount)}]
                                       {:abort-id :create-project-with-files})))}
                       "Create Project"))
         (case status
           :loading
           (comp/fragment
             (div :.ui.active.dimmer
                  (div :.ui.text.loader
                       (case progress-phase
                         ;; phase is one of #{:sending :receiving :complete :failed}
                         :sending
                         (str "Uploading " send-progress "% ... (")

                         ;; NOTE: the following values for progress-phase are possible but do not need UI
                         ;; indication at the moment:
                         ;;     :receiving
                         ;;     :complete
                         ;;     :failed

                         ;; default
                         "Almost ready ... (")
                       (a {:onClick (fn []
                                      (app/abort! this :create-project-with-files))}
                          "cancel")
                       ")"))
             (div :.ui.bottom.attached.progress
                  (div :.bar {:style {:transitDuration "300ms"
                                      :width           (str send-progress "%")}})))
           ;; TODO
           ;; :error
           ;; :complete
           nil))))

(def ui-account-upload-new-project (comp/factory AccountUploadNewProject))

(defsc MainSessionView [this {:keys         [create-new-project]
                              :session/keys [valid? account]
                              :as           props}]
  {:ident         (fn [] [:component/id :session])
   :query         [:session/valid?
                   {:session/account (comp/get-query SessionAccount)}
                   {:create-new-project (comp/get-query AccountUploadNewProject)}]
   :initial-state {:create-new-project {}}}
  (div :.ui.container
       (div :.ui.segment
            (if valid?
              (div {}
                   (h2 (str "Hello, " (or (:account/email account) "The unknown one") "!"))
                   (ui-session-account account)
                   (ui-account-upload-new-project create-new-project)
                   )
              (div {} "Logged out")))))

(def ui-main-session-view (comp/factory MainSessionView))


(defsc Main [this {:main/keys [main-session-view]}]
  {:ident         (fn [] [:component/id :main])
   :query         [{:main/main-session-view (comp/get-query MainSessionView)}]
   :initial-state {:main/main-session-view {}}
   :route-segment ["main"]}
  (ui-main-session-view main-session-view))

(defsc Settings [this {:keys [:account/time-zone :account/real-name] :as props}]
  {:query         [:account/time-zone :account/real-name]
   :ident         (fn [] [:component/id :settings])
   :route-segment ["settings"]
   :initial-state {}}
  (div :.ui.container.segment
       (h3 "Settings")
       (div
         (p (b "Name: ") real-name)
         (p (b "Time Zone: ") time-zone))))

(dr/defrouter TopRouter [this props]
  {:router-targets [Main Signup SignupSuccess Settings]})

(def ui-top-router (comp/factory TopRouter))

(defsc Session
  "Session representation. Used primarily for server queries. On-screen representation happens in Login component."
  [_ _]
  {:query         [:session/valid?
                   {:session/account (comp/get-query SessionAccount)}]
   :ident         (fn [] [:component/id :session])
   ;; :pre-merge     (fn [{:keys [data-tree]}]
   ;;                  (merge {:session/valid? false
   ;;                          :session/account {}}
   ;;                         data-tree))
   :initial-state {:session/valid?  false
                   :session/account {}}})

(defsc TopChrome [this {active-remotes ::app/active-remotes

                        :root/keys [router current-session login]

                        :as props}]
  {:ident         (fn [] [:component/id :top-chrome])
   :query         [{:root/router (comp/get-query TopRouter)}
                   {:root/current-session (comp/get-query Session)}
                   {:root/login (comp/get-query Login)}
                   [::uism/asm-id ::TopRouter]
                   [::app/active-remotes '_]]
   :initial-state {:root/router          {}
                   :root/login           {}
                   :root/current-session {}}
   :css           [[:.floating {:position "absolute !important"
                                :z-index  1000
                                :left     "0px"
                                :top      "0px"}]]
   }
  (let [current-tab        (some-> (dr/current-route this this) first keyword)
        {:keys [floating]} (css/get-classnames TopChrome)]
    (div :.ui.container
         (div :.ui.secondary.pointing.menu
              (a :.item
                 {:classes [(when (= :main current-tab) "active")]
                  :href    "/main"}
                 "Main")
              (a :.item
                 {:classes [(when (= :settings current-tab) "active")]
                  :href    "/settings"}
                 "Settings")
              (div :.right.menu
                   (ui-login login)))
         (div :.ui.grid
              (div :.ui.row
                   (ui-top-router router)))
         (when (seq active-remotes) (div {:classes [floating]} (str "Communicating with {" (str/join ", " active-remotes) "} ...")))
         ;; (div {:classes [floating]} (str "Remotes (" (str/join ", " active-remotes) ") are processing ..."))
         )))

(def ui-top-chrome (comp/factory TopChrome))

(defsc Root [this {:root/keys [top-chrome]}]
  {:query         [{:root/top-chrome (comp/get-query TopChrome)}]
   :initial-state {:root/top-chrome {}}}
  (ui-top-chrome top-chrome))

;; 

(comment
  ;; the entire db
  ;; {:com.fulcrologic.fulcro.ui-state-machines/asm-id
  ;;  {:app.ui.root/TopRouter
  ;;   #:com.fulcrologic.fulcro.ui-state-machines{:asm-id                :app.ui.root/TopRouter,
  ;;                                              :state-machine-id      com.fulcrologic.fulcro.routing.dynamic-routing/RouterStateMachine,
  ;;                                              :active-state          :routed,
  ;;                                              :ident->actor          {[:com.fulcrologic.fulcro.routing.dynamic-routing/id
  ;;                                                                       :app.ui.root/TopRouter]
  ;;                                                                      :router},
  ;;                                              :actor->ident          {:router
  ;;                                                                      [:com.fulcrologic.fulcro.routing.dynamic-routing/id
  ;;                                                                       :app.ui.root/TopRouter]},
  ;;                                              :actor->component-name {},
  ;;                                              :active-timers         {},
  ;;                                              :local-storage         {:pending-path-segment [],
  ;;                                                                      :target               [:component/id :main],
  ;;                                                                      :path-segment         ["main"]}},
  ;;   :app.model.session/session
  ;;   #:com.fulcrologic.fulcro.ui-state-machines{:asm-id                    :state-machine-id
  ;;                                              :app.model.session/session app.model.session/session-machine,
  ;;                                              :active-state              :state/logged-in,
  ;;                                              :ident->actor              {[:component/id :login]
  ;;                                                                          :actor/login-form,
  ;;                                                                          [:component/id :session]
  ;;                                                                          :actor/current-session},
  ;;                                              :actor->ident              #:actor{:login-form
  ;;                                                                                 [:component/id :login],
  ;;                                                                                 :current-session
  ;;                                                                                 [:component/id
  ;;                                                                                  :session]},
  ;;                                              :actor->component-name     #:actor{:login-form
  ;;                                                                                 :app.ui.root/Login,
  ;;                                                                                 :current-session
  ;;                                                                                 :app.ui.root/Session},
  ;;                                              :active-timers             {},
  ;;                                              :local-storage             {}}},
  ;;  :com.fulcrologic.fulcro.algorithms.form-state/forms-by-ident
  ;;  {{:table :component/id, :row :signup} #:com.fulcrologic.fulcro.algorithms.form-state{:id             [:component/id :signup],
  ;;                                                                                       :fields         #{:account/password
  ;;                                                                                                         :account/password-again
  ;;                                                                                                         :account/email},
  ;;                                                                                       :pristine-state #:account{:password "",
  ;;                                                                                                                 :password-again
  ;;                                                                                                                 "",
  ;;                                                                                                                 :email    ""},
  ;;                                                                                       :subforms       {},
  ;;                                                                                       :complete?      #{:account/password
  ;;                                                                                                         :account/password-again
  ;;                                                                                                         :account/email}}},
  ;;  :app.model.session/current-session                 [:component/id :session],
  ;;  :fulcro.inspect.core/app-id                        "app.ui.root/Root",
  ;;  :root/top-chrome                                   [:component/id :top-chrome],
  ;;  :fulcro.inspect.core/app-uuid                      #uuid "d234b6bf-3aaa-43a2-b4a1-42d1ad45c9d2",
  ;;  :com.fulcrologic.fulcro.routing.dynamic-routing/id #:app.ui.root{:TopRouter
  ;;                                                                   {:com.fulcrologic.fulcro.routing.dynamic-routing/id            :app.ui.root/TopRouter,
  ;;                                                                    :com.fulcrologic.fulcro.routing.dynamic-routing/current-route [:component/id :main],
  ;;                                                                    :alt0                                                         [:component/id :signup],
  ;;                                                                    :alt1                                                         [:component/id :signup-success],
  ;;                                                                    :alt2                                                         [:component/id :settings]}},
  ;;  :component/id                                      {:main           #:main{:welcome-message "Hi!"},
  ;;                                                      :signup         {:account/email                                       "hello@hello.com",
  ;;                                                                       :account/password                                    "asdfasdf",
  ;;                                                                       :account/password-again                              "asdfasdf",
  ;;                                                                       :com.fulcrologic.fulcro.algorithms.form-state/config [:com.fulcrologic.fulcro.algorithms.form-state/forms-by-ident {:table :component/id, :row :signup}]},
  ;;                                                      :signup-success {},
  ;;                                                      :settings       {},
  ;;                                                      :session        {:com.wsscode.pathom.core/reader-error "class clojure.lang.ExceptionInfo: Invalid credentials - {:username \"hello@hello.com\"}",
  ;;                                                                       :session/valid?                       true,
  ;;                                                                       :account/name                         "hello@hello.com"},
  ;;                                                      :login          {:account/email "hello@hello.com", :ui/error "", :ui/open? false},
  ;;                                                      :top-chrome     #:root{:router          [:com.fulcrologic.fulcro.routing.dynamic-routing/id
  ;;                                                                                               :app.ui.root/TopRouter],
  ;;                                                                             :login           [:component/id :login],
  ;;                                                                             :current-session [:component/id :session]}},
  ;;  :com.fulcrologic.fulcro.components/queries         {"app.ui.root/TopRouter"                                   {:query         [:com.fulcrologic.fulcro.routing.dynamic-routing/id
  ;;                                                                                                                                 [:com.fulcrologic.fulcro.ui-state-machines/asm-id :app.ui.root/TopRouter]
  ;;                                                                                                                                 #:com.fulcrologic.fulcro.routing.dynamic-routing{:current-route
  ;;                                                                                                                                                                                  "app.ui.root/Main"}],
  ;;                                                                                                                 :id            "app.ui.root/TopRouter",
  ;;                                                                                                                 :component-key :app.ui.root/TopRouter},
  ;;                                                      "app.ui.root/Main"                                        {:query         [:main/welcome-message],
  ;;                                                                                                                 :id            "app.ui.root/Main",
  ;;                                                                                                                 :component-key :app.ui.root/Main},
  ;;                                                      "app.ui.root/Settings"                                    {:query         [:account/time-zone :account/real-name],
  ;;                                                                                                                 :id            "app.ui.root/Settings",
  ;;                                                                                                                 :component-key :app.ui.root/Settings},
  ;;                                                      "app.ui.root/SignupSuccess"                               {:query         [*],
  ;;                                                                                                                 :id            "app.ui.root/SignupSuccess",
  ;;                                                                                                                 :component-key :app.ui.root/SignupSuccess},
  ;;                                                      "app.ui.root/Signup"                                      {:query         [:account/email
  ;;                                                                                                                                 :account/password
  ;;                                                                                                                                 :account/password-again #:com.fulcrologic.fulcro.algorithms.form-state{:config "com.fulcrologic.fulcro.algorithms.form-state/FormConfig"}],
  ;;                                                                                                                 :id            "app.ui.root/Signup",
  ;;                                                                                                                 :component-key :app.ui.root/Signup},
  ;;                                                      "com.fulcrologic.fulcro.algorithms.form-state/FormConfig" {:query
  ;;                                                                                                                 [:com.fulcrologic.fulcro.algorithms.form-state/id
  ;;                                                                                                                  :com.fulcrologic.fulcro.algorithms.form-state/fields
  ;;                                                                                                                  :com.fulcrologic.fulcro.algorithms.form-state/complete?
  ;;                                                                                                                  :com.fulcrologic.fulcro.algorithms.form-state/subforms
  ;;                                                                                                                  :com.fulcrologic.fulcro.algorithms.form-state/pristine-state],
  ;;                                                                                                                 :id            "com.fulcrologic.fulcro.algorithms.form-state/FormConfig",
  ;;                                                                                                                 :component-key :com.fulcrologic.fulcro.algorithms.form-state/FormConfig}},
  ;;  :com.fulcrologic.fulcro.application/active-remotes #{}}



  ;; (comp/get-query TopChrome)


  {:ui/error                                  :account/email,
   [:component/id :session]                   #:session{:valid? false}
   [::uism/asm-id :app.model.session/session] #::uism{:asm-id
                                                      :app.model.session/session,
                                                      :state-machine-id
                                                      #::uism{:actors           #{:actor/login-form
                                                                                  :actor/current-session},
                                                              :aliases          {:username       [:actor/login-form
                                                                                                  :account/email],
                                                                                 :error          [:actor/login-form
                                                                                                  :ui/error],
                                                                                 :modal-open?    [:actor/login-form
                                                                                                  :ui/open?],
                                                                                 :session-valid? [:actor/current-session
                                                                                                  :session/valid?],
                                                                                 :current-user   [:actor/current-session
                                                                                                  :account/id]},
                                                              :states           {:initial                #::uism{:target-states #{:state/logged-out
                                                                                                                                  :state/logged-in},
                                                                                                                 :events        {::uism/started
                                                                                                                                 #::uism{:handler
                                                                                                                                         "#object[Function]"},
                                                                                                                                 :event/failed
                                                                                                                                 #::uism{:target-state
                                                                                                                                         :state/logged-out},
                                                                                                                                 :event/complete
                                                                                                                                 #::uism{:target-states
                                                                                                                                         #{:state/logged-out
                                                                                                                                           :state/logged-in},
                                                                                                                                         :handler
                                                                                                                                         "#object[Function]"}}},
                                                                                 :state/checking-session #::uism{:events #:event{:toggle-modal #::uism{:handler "#object[Function]"},
                                                                                                                                 :failed       #::uism{:target-states #{:state/logged-out},
                                                                                                                                                       :handler       "#object[Function]"},
                                                                                                                                 :complete     #::uism{:target-states #{:state/logged-out
                                                                                                                                                                        :state/logged-in},
                                                                                                                                                       :handler       "#object[Function]"}}},
                                                                                 :state/logged-in        #::uism{:events #:event{:toggle-modal #::uism{:handler "#object[Function]"},
                                                                                                                                 :logout       #::uism{:target-states #{:state/logged-out},
                                                                                                                                                       :handler       "#object[app$model$session$logout]"}}},
                                                                                 :state/logged-out       #::uism{:events #:event{:toggle-modal   #::uism{:handler
                                                                                                                                                         "#object[Function]"},
                                                                                                                                 :login-by-email #::uism{:target-states #{:state/checking-session},
                                                                                                                                                         :handler       "#object[app$model$session$login_by_email]"}}}},
                                                              :state-machine-id app.model.session/session-machine},
                                                      :active-state          :state/logged-out,
                                                      :ident->actor          {[:component/id :login]   :actor/login-form,
                                                                              [:component/id :session] :actor/current-session},
                                                      :actor->ident          #:actor{:login-form      [:component/id :login],
                                                                                     :current-session [:component/id :session]},
                                                      :actor->component-name #:actor{:login-form
                                                                                     :app.ui.root/Login,
                                                                                     :current-session
                                                                                     :app.ui.root/Session},
                                                      :active-timers         {},
                                                      :local-storage         {}}}

  (react-dom-server/renderToString
    (div :.ui.container
         (div :.ui.secondary.pointing.menu
              (a :.item {:classes ["active"]
                         :onClick (fn [e]
                                    (js/console.log ["main"])
                                    (.preventDefault e))}
                 "Main")
              (a :.item {:classes ["active"]
                         :onClick (fn []
                                    (js/console.log ["settings"])
                                    (.preventDefault e))}
                 "Settings")
              (div :.right.menu
                   "ui-login login"))
         (div :.ui.grid
              (div :.ui.row
                   "ui-top-router router"))))

  {{:table :component/id, :row :signup}
   {:com.fulcrologic.fulcro.algorithms.form-state/id
    [:component/id :signup],
    :com.fulcrologic.fulcro.algorithms.form-state/fields
    #{:account/password :account/password-again :account/email},
    :com.fulcrologic.fulcro.algorithms.form-state/pristine-state
    {:account/password       "",
     :account/password-again "",
     :account/email          ""},
    :com.fulcrologic.fulcro.algorithms.form-state/subforms {}}})
