(ns app.model.run
  (:require
   [app.model.file :as file]
   [app.util :as util]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [com.fulcrologic.fulcro.networking.file-upload :as fu]
   [com.fulcrologic.fulcro.server.api-middleware :as fmw]
   [com.fulcrologic.guardrails.core :refer [=> >defn ? |]]
   [com.wsscode.pathom.connect :as pc :refer [defmutation defresolver]]
   [datahike.api :as d]
   [taoensso.timbre :as log]
   [app.server-components.config :as config]
   [clojure.java.io :as io]
   [me.raynes.fs :as fs]
   [clojure.core.async :as async])
  (:import [java.io FileNotFoundException]))


(defresolver run [{{output ::pc/output} ::pc/resolver-data
                   :keys                [conn]
                   :as                  env}
                  {:run/keys [id]}]
  {::pc/input  #{:run/id}
   ::pc/output [:run/id
                :run/name
                :run/status
                :run/message
                {:run/output-files [:file/id]}
                ]}
  (log/spy output)
  (d/pull @conn output [:run/id id]))

;; TODO: rename this to "get-run-file" -- as it returns a file object rather than a string
(defn get-run-path
  [run-id]
  (let [base-path  (:run-base-path config/config)
        run-id-str (str run-id)
        part-1     (subs run-id-str 0 2)
        part-2     (subs run-id-str 2)]
    (io/file base-path part-1 part-2)))

;; Create a run under the account `account-id`. Run the default workflow on all qualified files in project `project-id`.
;;
;;     * generate a UUID for the run
;;     * create a pwd (<base-path>/<run-id>)
;;     * retrive the list of files in the project
;;     * soft link all files to the pwd
;;     * run nextflow on pwd
;;     * register the run on the database
;;
(defmutation run-project
  [{:keys [conn ring/request]
    :as   env}
   {project-id :project-id
    ;; account-id is needed because each account has its own managed runs
    account-id :account-id}]
  {}
  (let [
        ;; TODO: confirm that the initiator (as identified by initiator-account-id) has the right to remove the project
        initiator-id (get-in request [:session :session/account :account/id])
        run-id       (util/uuid)
        ;; TODO: generate memorable run-names using the WordNet database.
        run-pwd      (get-run-path run-id)]

    (d/transact conn [{:account/id   account-id
                       :account/runs [{:run/id     run-id
                                       :run/status :initiating
                                       :run/name   (str "run-project (" (subs (str project-id) 0 6) ")")}]}])

    ;; NOTE: The reason we need to have this go-block here instead of simply sending the job
    ;; into a channel is because describing exactly what we need to do for the initialization
    ;; is part of this run-project function's particular implementation. Different jobs will
    ;; be initialized differently, and so far I don't think it's easy to systematically serialize
    ;; "how to initialize"
    (async/go
      ;; TODO: the file system preparations should be part of the initiation, if it fails
      ;; we should immediately change the run's state to failed.
      (try
        (fs/mkdirs run-pwd)
        (let [query-all-files-in-a-project
              ' [:find
                 [(pull ?files
                        [:file/id
                         :file/name
                         :file/size]) ...]
                 :in $ ?id
                 :where
                 [?project :project/id ?id]
                 [?project :project/files ?files]
                 ;; [?e :account/id]
                 ]
              files (d/q query-all-files-in-a-project @conn project-id)]
          (log/spy files)
          (doseq [file files]
            (let [path-in-pwd       (io/file run-pwd "input_files" (:file/name file))
                  path-in-warehouse (file/get-file-path (:file/id file))]
              (log/spy path-in-pwd)
              (log/spy path-in-warehouse)
              (fs/mkdirs (.getParentFile path-in-pwd))
              (fs/sym-link path-in-pwd path-in-warehouse))))

        (d/transact conn [{:run/id     run-id
                           :run/status :initiated}])

        ;; if the jrd is running, it will periodically look for the next
        ;; "initiated" job in the database

        (catch Exception e
          (log/error e)
          (d/transact conn [{:run/id      run-id
                             :run/status  :initiation-failed
                             :run/message (str e)}])))))

  {:account/id account-id})


;; This function rejects unless the run is _exactly_ :initiated
(defmutation retract-run
  [{:keys [conn ring/request]
    :as   env}
   {:keys [run-id]}]
  {}
  (d/transact conn [[:db/cas [:run/id run-id] :run/status :initiated :retracted]])
  {:run/id run-id})

(defmutation stop-run
  [{:keys [conn ring/request]
    :as   env}
   {:keys [run-id]}]
  {}
  (d/transact conn [{:run/id     run-id
                     :run/status :stopped}])
  ;; (log/debug "DEBUG [stop-run]: " (d/q '[:find [(pull ?run [:run/id
  ;;                                                           :run/status])]
  ;;                                        :where
  ;;                                        [?run :run/id run-id]
  ;;                                        ]
  ;;                                      @conn))

  {:run/id run-id})

(defmutation remove-run-from-account
  [{:keys [conn ring/request]
    :as   env}
   {:keys [run-id account-id]}]
  {}
  (d/transact conn [[:db/retract [:account/id account-id] :account/runs [:run/id run-id]]])
  (async/go
    ;; TODO: check if the run is orphaned and if so, forcefully terminate and clear it
    )
  {:account/id account-id})

(def resolvers [run-project run stop-run retract-run remove-run-from-account])
