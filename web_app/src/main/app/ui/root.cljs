(ns app.ui.root
  (:require
   [app.model.project :as project]
   [app.model.session :as session]
   [app.model.run :as run]
   [app.routing :as routing]
   [app.model.auto-refresh :as auto-refresh]
   [clojure.string :as str]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro-css.css :as css]
   [com.fulcrologic.fulcro.dom :as dom :refer [textarea a b i img button div h1 h2 h3 h4 input label p span form code pre iframe]]
   [com.fulcrologic.fulcro.dom.events :as evt]
   [com.fulcrologic.fulcro.dom.html-entities :as ent]
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.fulcro.networking.file-upload :as fu]
   [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
   [com.fulcrologic.fulcro.ui-state-machines :as uism]
   [taoensso.timbre :as log]
   ["filesize" :as filesize]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [clojure.core.async :as async]))

(declare
  SessionAccount ui-session-account
  AccountUploadNewProject ui-account-upload-new-project
  AutoRefresh ui-auto-refresh
  File ui-file
  Login
  Main
  MainSessionView ui-main-session-view
  ProjectItem ui-project-item
  Root
  SessionQ
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
       (cond
         (re-matches #"^.*\.fastq(\.gz)?$" name) (i :.large.icon.dna)
         (re-matches #"^.*\.txt$" name)          (i :.large.icon.file)
         :else                                   (i :.large.icon.file))
       (div :.content
            (div :.header
                 (a {:href (str "/files/" id "/download") :data-pushy-ignore true} (str name " (" (filesize size) ")")))
            (div :.description (str id)))))

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
                 (map ui-file (sort-by #(:file/name %) files))))))

(def ui-project-item (comp/computed-factory ProjectItem {:keyfn :project/id}))


(defsc RunItem [this
                {:run/keys [id name status message stdout stderr]}
                {:keys [stop-run retract-run remove-run]}]
  {:ident         :run/id
   :query         [:run/id :run/name :run/status
                   :run/message :run/stdout :run/stderr]
   :initial-state {}
   :css           [[:.retracted {:text-decoration "line-through"
                                 :opacity         0.3}]
                   [:.failed {:color "red"}]
                   [:.succeeded {:color "green"}]]}
  (let [classes (css/get-classnames RunItem)]
    (div :.item
         (i :.large.icon.plane
            {:classes (filter some? [(when (= :retracted status) (:retracted classes))
                                     (when (= :failed status) (:failed classes))
                                     (when (= :succeeded status) (:succeeded classes))])})
         (div :.content
              (div :.header
                   name
                   " " (str status)
                   (when (= :initiated status)
                     (comp/fragment " - " (a {:onClick #(retract-run id)} "retract")))
                   (when (= :running status)
                     (comp/fragment " - " (a {:onClick #(stop-run id)} "stop")))
                   (when (contains? #{:retracted :initiation-failed :succeeded :failed :stopped} status)
                     (comp/fragment " - " (a {:onClick #(remove-run id)} "remove")))
                   ;; TODO: Have a function to download all files as a zipped archive
                   ;; " - " (a {:onClick #(run-project id)} "run")
                   )
              (div :.description (str id))
              (when (= :succeeded status)
                (let [report-url (str "/runs/" id "/output-files/index.html")]
                  (div :.ui.segment
                       (h4 :.ui.header (a {:href report-url :data-pushy-ignore true} "Report"))
                       (iframe {:width 1000:height 500 :frameBorder 0 :src report-url}))))
              (when (seq message)
                (div :.ui.segment
                     (h4 :.ui.header "Message")
                     (pre (str message))))
              (when (seq stdout)
                (div :.ui.segment
                     (h4 :.ui.header "Worker stdout")
                     (pre (str stdout))))
              (when (seq stderr)
                (div :.ui.segment
                     (h4 :.ui.header "Worker stderr")
                     (pre (str stderr))))))))

(def ui-run-item (comp/computed-factory RunItem {:keyfn :run/id}))

(defsc SessionAccount [this {:account/keys [projects runs]}]
  {:ident          :account/id
   :query          [:account/id
                    :account/email
                    {:account/projects (comp/get-query ProjectItem)}
                    {:account/runs (comp/get-query RunItem)}]
   :initLocalState (fn [this props]
                     {:retract-run    (fn [run-id]
                                        (comp/transact! this [{(run/retract-run
                                                                 {:run-id run-id})
                                                               (comp/get-query RunItem)}]))
                      :stop-run       (fn [run-id]
                                        (comp/transact! this [{(run/stop-run
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
  (div
    (when (seq runs)
      (div :.ui.segment {:style {:overflow "auto"}}
           (h3 :.ui.header "Runs")
           (div :.ui.relaxed.divided.list {}
                (for [run runs]
                  (ui-run-item run (select-keys (comp/get-state this) [:stop-run :retract-run :remove-run]))))))
    (div :.ui.segment
         (h3 :.ui.header "Projects")
         (div :.ui.relaxed.divided.list {}
              (for [project projects]
                (ui-project-item project (select-keys (comp/get-state this) [:remove-project :run-project])))))))

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

(defsc Login [this {:ui/keys [error open? state loading? email]
                    :as      props}]
  {:ident (fn [] [:component/id :login])
   :query [:ui/open?
           :ui/error
           :ui/state
           :ui/loading?
           :ui/email

           {[:component/id :session] (comp/get-query SessionQ)}
           ;; NOTE: Why this instead of having :ident be (fn [] [:component/id :session])?
           ;; The reason is to avoid :ui/xxx be put into [:component/id :session]
           ;; The sacrifice is that we could never do (df/load! this :xxx Login), but that's okay because we would never do that.
           ]
   :initial-state {:ui/error ""
                   :ui/state :initial
                   :ui/email ""}
   :css           [[:.floating-menu {:position "absolute !important"
                                     :z-index  1000
                                     :width    "300px"
                                     :right    "0px"
                                     :top      "50px"}]]}
  ;; (print "props =" props)
  (let [session                 (get props [:component/id :session])
        account                 (:session/account session)
        account-id              (:account/id account)
        account-email           (:account/email account)
        {:keys [floating-menu]} (css/get-classnames Login)
        password                (or (comp/get-state this :password) "")] ; c.l. state for security
    (div
      (div :.right.menu
           (case state
             :initial    (span :.item "Initializing ...")
             :logged-in  (button :.item
                                 {:onClick #(uism/trigger! this :session :event/logout)}
                                 (span :.ui.image.label (img {:src "/avataaars.svg"}) (str account-email)) ent/nbsp "Log out")
             :logged-out (div :.item {:style   {:position "relative"}
                                      :onClick #(uism/trigger! this :session :event/toggle-modal)}
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
                                                   :onChange #(m/set-string! this :ui/email :event %)})
                                           (field {:label    "Password"
                                                   :type     "password"
                                                   :value    password
                                                   :onChange #(comp/set-state! this {:password (evt/target-value %)})})
                                           (div :.ui.error.message error)
                                           (div :.ui.field
                                                (button :.ui.button
                                                        {:type    "button"
                                                         :onClick (fn [] (uism/trigger! this :session :event/login-by-email
                                                                                        {:email    email
                                                                                         :password password}))
                                                         :classes [(when loading? "loading")]} "Login"))
                                           (div :.ui.message
                                                (p "Don't have an account?")
                                                (a {:onClick (fn []
                                                               (uism/trigger! this :session :event/toggle-modal {})
                                                               (routing/route-to! "/signup"))}
                                                   "Please sign up!")))))))))))

(def ui-login (comp/factory Login))

(defsc AccountUploadNewProject [this {:ui/keys [new-project-name server-side-files-str] :as props}]
  {:ident         (fn [] [:component/id :upload-new-project-panel])
   :query         [{[:component/id :session] [{:session/account [:account/id]}]}
                   :ui/new-project-name
                   :ui/server-side-files-str
                   [df/marker-table '_]]
   :initial-state {:ui/new-project-name ""}}
  (let [marker           (get-in props [df/marker-table :app.model.project/create-project-with-files])
        status           (:status marker)
        progress-phase   (:progress-phase marker)
        overall-progress (:overall-progress marker)
        send-progress    (:send-progress marker)
        receive-progress (:receive-progress marker)]
    (div :.ui.segment
         (form :.ui.form
               (div :.field
                    (label "Project Name")
                    (input {:value       (or new-project-name "")
                            :placeholder "Please type a project name"
                            :onChange    (fn [evt] (m/set-string! this :ui/new-project-name :event evt))}))
               (div :.field
                    (label "FastQ Files")
                    (input {:type     "file"
                            :multiple true
                            :onChange (fn [evt]
                                        (let [files (fu/evt->uploads evt)]
                                          (comp/set-state! this {:fastq-files files})))}))
               (div :.field
                    (label "FastQ Files (Server-side)")
                    (input {:type        "text"
                            :disabled    true
                            :value       (or server-side-files-str "")
                            :placeholder "Type path(s) to *.fastq.gz files on the server (separated by \";\"), or leave empty."
                            :onChange    (fn [evt] (m/set-string! this :ui/server-side-files-str :event evt))}))
               (button {:type    "button"
                        :onClick (fn []
                                   (let [fastq-files (comp/get-state this :fastq-files)]
                                     (comp/transact!
                                       this
                                       [{(project/create-project-with-files
                                           (fu/attach-uploads
                                             {:project-name new-project-name
                                              :account-id   (get-in props [[:component/id :session] :session/account :account/id])}
                                             fastq-files))
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

(defsc MainSessionView [this {:ui/keys [create-new-project auto-refresh]
                              :as      props}]
  {:ident         (fn [] [:component/id :main-session-view])
   :query         [{[:component/id :session] (comp/get-query SessionQ)}
                   {:ui/create-new-project (comp/get-query AccountUploadNewProject)}
                   {:ui/auto-refresh (comp/get-query AutoRefresh)}]
   :initial-state {:ui/create-new-project {}
                   :ui/auto-refresh       {}
                   }}
  (let [{:session/keys [valid? account]} (get props [:component/id :session])]
    (div :.ui.container
         (div :.ui.segment
              (if valid?
                (div {}
                     (h2 (str "Hello, " (or (:account/email account) "The unknown one") "!"))
                     (ui-auto-refresh auto-refresh)
                     (ui-session-account account)
                     (ui-account-upload-new-project create-new-project)
                     )
                (div {} "Logged out"))))))

(def ui-main-session-view (comp/factory MainSessionView))


(defsc AutoRefresh [this _]
  {:ident             (fn [] [:component/id :auto-refresh])
   :query             [[::uism/asm-id :auto-refresh]]
   :initial-state     {}
   :initLocalState    (fn [this props]
                        (letfn [(refresh [] (df/refresh! (comp/class->any this SessionAccount)))]
                          {:turn-on-auto-refresh  #(uism/trigger! this :auto-refresh :event/start {:tick-fn refresh})
                           :turn-off-auto-refresh #(uism/trigger! this :auto-refresh :event/stop)
                           :refresh               refresh}))
   :componentDidMount (fn [this props]
                        ((comp/get-state this :turn-on-auto-refresh)))}
  (let [current-state (log/spy (uism/get-active-state this :auto-refresh))]
    (div :.ui.segment
         (button :.ui.compact.icon.button
                 {:onClick (comp/get-state this :refresh)
                  :title   "Manual refresh"}
                 (i :.refresh.icon))
         (case current-state
           :state/running (button :.ui.compact.positive.button
                                  {:onClick (comp/get-state this :turn-off-auto-refresh)}
                                  "Stop auto-refresh")
           :state/stopped (button :.ui.compact.button
                                  {:onClick (comp/get-state this :turn-on-auto-refresh)}
                                  "Start auto-refresh")
           (button :.ui.compact.button
                   {:disabled true}
                   "Loading ...")))))

(def ui-auto-refresh (comp/factory AutoRefresh))


(defsc Main [this {:main/keys [main-session-view]}]
  {:ident         (fn [] [:component/id :main])
   :query         [{:main/main-session-view (comp/get-query MainSessionView)}]
   :initial-state {:main/main-session-view {}}
   :route-segment ["main"]}
  (div
    (ui-main-session-view main-session-view)))

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

(defsc SessionQ
  "Session representation. Used primarily for server queries. On-screen representation happens in Login component."
  [_ _]
  {
   ;; TODO: it's a bad practice for a pure query to depend on a view
   :query         [:session/valid?
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
                   {:root/current-session (comp/get-query SessionQ)}
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
         ;; (when (seq active-remotes) (div {:classes [floating]} (str "Communicating with {" (str/join ", " active-remotes) "} ...")))
         ;; (div {:classes [floating]} (str "Remotes (" (str/join ", " active-remotes) ") are processing ..."))
         )))

(def ui-top-chrome (comp/factory TopChrome))

(defsc Root [this {:root/keys [top-chrome]}]
  {:query         [{:root/top-chrome (comp/get-query TopChrome)}]
   :initial-state {:root/top-chrome {}}}
  (ui-top-chrome top-chrome))

;; 

(comment
  )
