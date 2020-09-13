(ns app.ui.root
  (:require
   [app.model.auto-refresh :as auto-refresh]
   [app.model.project :as project]
   [app.model.run :as run]
   [app.model.session :as session]
   [app.routing :as routing]

   [clojure.core.async                               :as async]
   [clojure.string                                   :as str]
   [com.fulcrologic.fulcro-css.css                   :as css]
   [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
   [com.fulcrologic.fulcro.algorithms.form-state     :as fs]
   [com.fulcrologic.fulcro.application               :as app]
   [com.fulcrologic.fulcro.components                :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.data-fetch                :as df]
   [com.fulcrologic.fulcro.dom                       :as dom :refer [textarea a b i img button div h1 h2 h3 h4 input label p span form code pre iframe main]]
   [com.fulcrologic.fulcro.dom.events                :as evt]
   [com.fulcrologic.fulcro.dom.html-entities         :as ent]
   [com.fulcrologic.fulcro.mutations                 :as m]
   [com.fulcrologic.fulcro.networking.file-upload    :as fu]
   [com.fulcrologic.fulcro.routing.dynamic-routing   :as dr]
   [com.fulcrologic.fulcro.ui-state-machines         :as uism]
   [com.fulcrologic.fulcro.algorithms.react-interop  :as interop]
   [taoensso.timbre                                  :as log]

   ["filesize" :as filesize]
   ))


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
            (div
              :.list
              {:style {:maxHeight 300
                       :overflow  "auto"}}
              (map ui-file (sort-by #(:file/name %) files))))))

(def ui-project-item (comp/computed-factory ProjectItem {:keyfn :project/id}))


(defsc RunItem [this
                {:ui/keys  [tab-value]
                 :run/keys [id name status message stdout stderr]}
                {:keys [stop-run retract-run remove-run]}]
  {:ident     :run/id
   :query     [:ui/tab-value
               :run/id :run/name :run/status
               :run/message :run/stdout :run/stderr]
   :pre-merge (fn [{:keys [current-normalized data-tree]}]
                (merge
                  {:ui/tab-value "messages"}
                  current-normalized
                  data-tree))
   :css       [[:.retracted {:text-decoration "line-through"
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
              (div :.ui.top.attached.tabular.menu
                   (a :.item {:classes [(when (= "messages" tab-value) "active")] :onClick #(m/set-string! this :ui/tab-value :value "messages")} "Messages")
                   (a :.item {:classes [(when (= "report" tab-value) "active")] :onClick #(m/set-string! this :ui/tab-value :value "report")} "Report")
                   (a :.item {:classes [(when (= "output-files" tab-value) "active")] :onClick #(m/set-string! this :ui/tab-value :value "output-files")} "Output Files"))
              (div
                :.ui.bottom.attached.segment
                (case tab-value
                  "messages"
                  (div
                    (when (seq message)
                      (div
                        (h4 :.ui.header "Message")
                        (pre (str message))))
                    (when (seq stdout)
                      (div
                        (h4 :.ui.header "Worker stdout")
                        (div
                          {:style {:maxWidth  1000
                                   :maxHeight 500
                                   :overflow  "auto"}}
                          (pre (str stdout)))))
                    (when (seq stderr)
                      (div
                        (h4 :.ui.header "Worker stderr")
                        (pre (str stderr)))))

                  "report"
                  (let [url (str "/runs/" id "/output-files/index.html")]
                    (div
                      (a :.ui.button {:href url :data-pushy-ignore true} "View in fullscreen")
                      (div :.ui.segment (iframe {:width 1000 :height 500 :frameBorder 0 :src url}))))

                  "output-files"
                  (let [url (str "/runs/" id "/output-files/")]
                    (div
                      (a :.ui.button {:href url :data-pushy-ignore true} "View in fullscreen")
                      (div :.ui.segment (iframe {:width 1000 :height 500 :frameBorder 0 :src url}))))

                  nil
                  ))))))

(def ui-run-item (comp/computed-factory RunItem {:keyfn :run/id}))

(defsc SessionAccount [this {:ui/keys      [tab-value
                                            create-project-modal-active?
                                            props-account-upload-new-project]
                             :account/keys [id projects runs]
                             :as           props}]
  {:ident          :account/id
   :query          [:ui/tab-value
                    :ui/create-project-modal-active?
                    {:ui/props-account-upload-new-project (comp/get-query AccountUploadNewProject)}
                    :account/id
                    :account/email
                    {:account/projects (comp/get-query ProjectItem)}
                    {:account/runs (comp/get-query RunItem)}
                    [df/marker-table :app.model.project/create-project-with-files]
                    ]
   :pre-merge      (fn [{:keys [current-normalized data-tree]}]
                     (merge
                       {:ui/tab-value                        :projects
                        :ui/create-project-modal-active?     false
                        :ui/props-account-upload-new-project (comp/initial-state AccountUploadNewProject {})
                        }
                       current-normalized
                       data-tree))
   :initial-state  {:account/runs                        [{}]
                    :ui/props-account-upload-new-project {}}
   :initLocalState (fn [this props]
                     {:retract-run
                      (fn [run-id]
                        (comp/transact! this [{(run/retract-run
                                                 {:run-id run-id})
                                               (comp/get-query RunItem)}]))

                      :stop-run
                      (fn [run-id]
                        (comp/transact! this [{(run/stop-run
                                                 {:run-id run-id})
                                               (comp/get-query RunItem)}]))

                      :remove-run
                      (fn [run-id]
                        (comp/transact! this [{(run/remove-run-from-account
                                                 {:run-id     run-id
                                                  :account-id (:account/id (comp/props this))})
                                               (comp/get-query SessionAccount)}]))

                      :remove-project
                      (fn [project-id]
                        (comp/transact! this [{(project/remove-project
                                                 {:project-id project-id
                                                  :account-id (:account/id (comp/props this))})
                                               (comp/get-query SessionAccount)}]))

                      :run-project
                      (fn [project-id]
                        (comp/transact! this [{(run/run-project
                                                 {:project-id project-id
                                                  :account-id (:account/id (comp/props this))})
                                               (comp/get-query SessionAccount)}]))

                      :on-create-project-done
                      (fn []
                        (m/set-value! this :ui/create-project-modal-active? false)
                        )})}
  (let [marker           (log/spy (get-in (log/spy props) [[df/marker-table :app.model.project/create-project-with-files]]))
        status           (:status marker)
        progress-phase   (:progress-phase marker)
        overall-progress (:overall-progress marker)
        send-progress    (:send-progress marker)
        receive-progress (:receive-progress marker)]
    (div
      {:style {:width 800}}
      (div :.ui.secondary.pointing.menu
           (map (fn [item] (a :.item {:key     (:value item)
                                      :onClick #(m/set-value! this :ui/tab-value (:value item))
                                      :classes [(when (= (:value item) tab-value) "active")]} (:label item)))
                [{:label (str "Projects (" (count projects) ")")
                  :value :projects}
                 {:label (str "Runs (" (count runs) ")")
                  :value :runs}]))
      (case tab-value
        :projects
        (div
          (div :.ui.segment
               (case status
                 :loading
                 (div :.ui.buttons
                      (div
                        :.ui.disabled.button
                        (case progress-phase
                          ;; phase is one of #{:sending :receiving :complete :failed}
                          :sending
                          (str "Uploading " send-progress "% ...")


                          ;; NOTE: the following values for progress-phase are possible but do not need UI
                          ;; indication at the moment since they will only take a fraction of a second:
                          ;;     :receiving
                          ;;     :complete
                          ;;     :failed

                          ;; default
                          "Almost ready ..."))
                      (button
                        :.ui.button
                        {:onClick (fn [] (app/abort! this :create-project-with-files))}
                        "Cancel"))

                 ;; TODO
                 ;; :error
                 ;; :complete
                 (button
                   :.ui.button
                   {:onClick #(m/toggle! this :ui/create-project-modal-active?)}
                   (i :.plus.icon)
                   "New Project ..."))
               ;; (if create-project-modal-active?
               ;;   (i :.angle.up.icon)
               ;;   (i :.angle.down.icon))

               (div
                 :.ui.page.dimmer
                 {:style   {:position "fixed"
                            :zIndex   1001}
                  :classes [(when create-project-modal-active? "active")]
                  :onClick #(m/set-value! this :ui/create-project-modal-active? false)
                  }
                 (div
                   :.content
                   {:onClick #(.stopPropagation %)}
                   (div
                     :.ui.raised.segment
                     (ui-account-upload-new-project
                       props-account-upload-new-project
                       {:on-done    (comp/get-state this :on-create-project-done)
                        :account-id (log/spy id)})))))
          (if (seq projects)
            (div
              :.ui.relaxed.divided.list
              (for [project projects]
                (ui-project-item project (select-keys (comp/get-state this) [:remove-project :run-project]))))
            (div "There's no projects.")))

        :runs
        (if (seq runs)
          (div
            :.ui.relaxed.divided.list
            (for [run runs]
              (ui-run-item run (select-keys (comp/get-state this) [:stop-run :retract-run :remove-run]))))
          (div "There's no runs."))

        nil))))

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

           {[:component/id :session] [{:session/account [:account/id
                                                         :account/email]}]}
           ;; {[:component/id :session] (comp/get-query SessionQ)}
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
      {:style {:margin 16}}
      (case state
        :initial    (div :.ui.segment
                         {:style {:height 100}}
                         (div :.ui.active.dimmer
                              (div :.ui.indeterminate.text.loader "Validating ...")))
        :logged-in  (div
                      {:style {:display       "flex"
                               :flexDirection "column"}}
                      (div
                        {:style {:textAlign "center"
                                 :margin    8
                                 :marginTop 16}}
                        (img
                          {:style {:width  64
                                   :height 64}
                           :src   "/avataaars.svg"})
                        (p :.ui.header {:style {:margin 0}} account-email)
                        (div
                          {:style {:marginTop 8}}
                          (button
                            :.ui.mini.button
                            {:onClick #(uism/trigger! this :session :event/logout)}
                            "Log out"))
                        )
                      )
        :logged-out (div
                      {:style {:display       "flex"
                               :flexDirection "column"}}
                      (h4 :.ui.header "You are logged out."
                          (p :.sub.header "Log in with your credentials."))
                      (div
                        (form :.ui.form {:classes [(when (seq error) "error")]}
                              (field {:label    "Email"
                                      :value    email
                                      :onChange #(m/set-string! this :ui/email :event %)})
                              (field {:label    "Password"
                                      :type     "password"
                                      :value    password
                                      :onChange #(comp/set-state! this {:password (evt/target-value %)})})
                              (div :.ui.error.message error)
                              (div :.ui.fluid.buttons
                                   (button :.ui.primary.button
                                           {:type    "button"
                                            :onClick (fn [] (uism/trigger! this :session :event/login-by-email
                                                                           {:email    email
                                                                            :password password}))
                                            :classes [(when loading? "loading")]} "Login")
                                   (button :.ui.button
                                           {:onClick (fn []
                                                       (uism/trigger! this :session :event/toggle-modal {})
                                                       (routing/route-to! "/signup"))}
                                           "Sign up"))))))
      )))

(def ui-login (comp/factory Login))

(defsc AccountUploadNewProject [this
                                {:ui/keys [new-project-name
                                           server-side-files-str]
                                 :as      props}
                                {:keys [account-id
                                        on-done]}
                                ]
  {:ident         (fn [] [:component/id :upload-new-project-panel])
   :query         [:ui/new-project-name
                   :ui/server-side-files-str]
   :initial-state {:ui/new-project-name ""}
   :pre-merge     (fn [{:keys [current-normalized data-tree]}]
                    (merge
                      {:ui/server-side-files-str ""
                       :ui/new-project-name      ""}
                      current-normalized
                      data-tree))}
  (div
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
          (div
            :.ui.actions
            (button
              :.ui.button
              {:type    "button"
               :onClick (fn []
                          (let [fastq-files (comp/get-state this :fastq-files)]
                            (comp/transact!!
                              this
                              [{(project/create-project-with-files
                                  (fu/attach-uploads
                                    {:project-name new-project-name
                                     :account-id   account-id}
                                    fastq-files))
                                (comp/get-query SessionAccount)}]
                              {:abort-id :create-project-with-files}))
                          (on-done))}
              "Create Project")))
    ))

(def ui-account-upload-new-project (comp/computed-factory AccountUploadNewProject))

(defsc MainSessionView [this {:ui/keys [auto-refresh]
                              :as      props}]
  {:ident (fn [] [:component/id :main-session-view])
   :query [{:ui/auto-refresh (comp/get-query AutoRefresh)}

           {[:component/id :session] (comp/get-query SessionQ)}
           ]
   :initial-state {:ui/auto-refresh {}}}
  (let [{:session/keys [valid? account]} (get props [:component/id :session])]
    (div
      (if valid?
        (div {}
             (ui-auto-refresh auto-refresh)
             (ui-session-account account))
        (div {} "")))))

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
  (let [current-state         (uism/get-active-state this :auto-refresh)
        turn-on-auto-refresh  (comp/get-state this :turn-on-auto-refresh)
        turn-off-auto-refresh (comp/get-state this :turn-off-auto-refresh) ]
    (div
      {:style {:display      "flex"
               :marginBottom 16}}
      (button
        :.ui.button
        {:onClick (comp/get-state this :refresh)}
        (i :.refresh.icon)
        "Refresh")
      (button
        :.ui.button
        {:classes [(when (= :state/running current-state) "orange")]
         :onClick #(if (= :state/running current-state)
                     (turn-off-auto-refresh)
                     (turn-on-auto-refresh))}
        (i :.stopwatch.icon)
        "Auto-refresh"))))

(def ui-auto-refresh (comp/factory AutoRefresh))


(defsc Main [this {:main/keys [main-session-view]}]
  {:ident         (fn [] [:component/id :main])
   :query         [{:main/main-session-view (comp/get-query MainSessionView)}]
   :initial-state {:main/main-session-view {}}
   :route-segment ["main"]}
  (div
    (ui-main-session-view main-session-view)))

(defsc About [this _]
  {:query         []
   :ident         (fn [] [:component/id :about])
   :route-segment ["about"]
   :initial-state {}}
  (div
    (img {:src "/idcov.svg"})))

(dr/defrouter TopRouter [this props]
  {:router-targets [Main
                    Signup
                    SignupSuccess
                    About]})

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




(defsc TopChrome [this {::app/keys [active-remotes]
                        :root/keys [router current-session login]

                        :as props}]
  {:ident         (fn [] [:component/id :top-chrome])
   :query         [
                   {:root/router (comp/get-query TopRouter)}
                   {:root/current-session (comp/get-query SessionQ)}
                   {:root/login (comp/get-query Login)}
                   [::uism/asm-id ::TopRouter]
                   [::app/active-remotes '_]]
   :initial-state {:root/router          {}
                   :root/login           {}
                   :root/current-session {}}
   :css           [
                   [:.active {:background "rgba(0, 0, 0, .12)"}]
                   [:.floating {:position "absolute !important"
                                :z-index  1000
                                :left     "0px"
                                :top      "0px"}]
                   [:.hr-paddings {:padding-left  16
                                   :padding-right 16}]]}
  (let [current-tab   (some-> (dr/current-route this this) first keyword)
        classnames    (css/get-classnames TopChrome)
        c-active      (:active classnames)
        c-floating    (:floating classnames)
        c-hr-paddings (:hr-paddings classnames)]
    (div
      {:style {:position    "relative"
               :marginLeft  256
               :paddingLeft 32
               :paddingTop  16}}
      (ui-top-router router)
      (div
        {:style {:position      "fixed"
                 :left          0
                 :top           0
                 :overflow      "auto"
                 :display       "flex"
                 :flexDirection "column"
                 :borderRight   "1px solid rgba(0,0,0,.12)"
                 :width         256
                 :height        "100%"
                 :marginRight   32}}
        (ui-login login)
        ;; (for [menu-item [{:label "Main"
        ;;                   :url   "/main"}]])
        (div
          {:style {
                   :marginTop   16
                   :marginLeft  16
                   :marginRight 16}}
          (div
            :.ui.vertical.fluid.menu
            (map
              #(a
                 :.item
                 {:key     (:value %)
                  :href    (:url %)
                  :classes [(when (= (:value %) current-tab) "active")
                            c-hr-paddings]}
                 (i :.icon {:classes [(:icon %)]})
                 (:label %))
              [{:label "Main"
                :icon  "home"
                :value :main
                :url   "/main"}
               {:label "About"
                :icon  "question circle"
                :value :about
                :url   "/about"}])))
        (div
          {:style {
                   :marginTop   16
                   :marginLeft  16
                   :marginRight 16}}))
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
