(ns app.model.project
  (:require [clojure.spec.alpha :as s]
            [app.model.file :as file]
            [clojure.string :as str]
            [com.fulcrologic.fulcro.server.api-middleware :as fmw]
            [com.fulcrologic.guardrails.core :refer [=> >defn ? |]]
            [com.wsscode.pathom.connect :as pc :refer [defmutation defresolver]]
            [datahike.api :as d]
            [com.fulcrologic.fulcro.networking.file-upload :as fu]
            [taoensso.timbre :as log]
            [app.util :as util]
            [app.server-components.config :as config]
            [clojure.java.io :as io]))


(defresolver project [{{output ::pc/output} ::pc/resolver-data
                       :keys                [conn]
                       :as                  env}
                      {:project/keys [id]}]
  {::pc/input  #{:project/id}
   ::pc/output [:project/id
                :project/name
                {:project/files [:file/id]}]}
  (d/pull @conn output [:project/id id]))

(defmutation create-project-with-files
  [{:keys [conn]} {:keys     [project-name account-id]
                   ::fu/keys [files]
                   :as       inputs}]
  {}
  ;; {:project-name "vcxbcvbc"
  ;;  :account-id   #uuid "12e59b5c-93f3-48ce-9ff0-5a972ffaf41a"
  ;;  ::fu/files    [{:filename     "foo.txt"
  ;;                  :content-type "text/plain"
  ;;                  :tempfile     #object[java.io.File 0x6a9d213f "/tmp/ring-multipart-17154054071924291961.tmp"]
  ;;                  :size         18}]}

  ;; TODO: need to reject if the account id doesn't exist
  (let [project-id (util/uuid)
        file-txs   (vec
                     (for [file files]
                       (file/link-file (:tempfile file)
                                       {:filename    (:filename file)
                                        :link-method :move})))]
    (d/transact conn (log/spy [{:account/id       account-id
                                :account/projects [{:project/id    project-id
                                                    :project/name  project-name
                                                    :project/files file-txs}]}])))

  {:account/id account-id})


(defmutation add-example-project
  [{:keys [conn]} {:keys [account-id] :as inputs}]
  {}
  (let [example-project-path (:example-project-path config/config)]
    (when (some? example-project-path)
      ;; if example-project-path is nil, then this mutation is a noop
      (let [files              (.listFiles (io/file example-project-path))
            files-tx           (mapv file/link-file files)
            example-project-tx {:project/id    (java.util.UUID/randomUUID)
                                :project/name  "Example project"
                                :project/files files-tx}]
        (d/transact conn [{:account/id       account-id
                           :account/projects [example-project-tx]}]))))

  {:account/id account-id})

(defmutation remove-project [{{{{initiator-account-id :account/id} :session/account} :session} :ring/request

                              :keys [conn]
                              :as   env}
                             {project-id :project-id
                              account-id :account-id}]
  {}
  ;; TODO: confirm that the initiator (as identified by initiator-account-id) has the right to remove the project
  (log/spy project-id)
  (log/spy account-id)
  (log/spy (d/transact conn [[:db/retract [:account/id account-id] :account/projects [:project/id project-id]]]))
  {:account/id account-id}
  )

(def resolvers [create-project-with-files remove-project project add-example-project])


;; (
;;  :ast
;;  :com.wsscode.pathom.connect/indexes
;;  :com.wsscode.pathom.connect/mutate-dispatch
;;  :com.wsscode.pathom.connect/pool-chan
;;  :com.wsscode.pathom.connect/resolver-dispatch
;;  :com.wsscode.pathom.connect/resolver-weights
;;  :com.wsscode.pathom.connect/source-mutation
;;  :com.wsscode.pathom.core/async-request-cache-ch
;;  :com.wsscode.pathom.core/entity
;;  :com.wsscode.pathom.core/entity-key
;;  :com.wsscode.pathom.core/entity-path-cache
;;  :com.wsscode.pathom.core/errors*
;;  :com.wsscode.pathom.core/parent-query
;;  :com.wsscode.pathom.core/path
;;  :com.wsscode.pathom.core/placeholder-prefixes
;;  :com.wsscode.pathom.core/plugin-actions
;;  :com.wsscode.pathom.core/plugins
;;  :com.wsscode.pathom.core/process-error
;;  :com.wsscode.pathom.core/reader
;;  :com.wsscode.pathom.core/request-cache
;;  :com.wsscode.pathom.core/root-query
;;  :com.wsscode.pathom.parser/active-paths
;;  :com.wsscode.pathom.parser/done-signal*
;;  :com.wsscode.pathom.parser/key-process-timeout
;;  :com.wsscode.pathom.parser/key-watchers
;;  :com.wsscode.pathom.parser/parallel?
;;  :com.wsscode.pathom.parser/waiting
;;  :config
;;  :conn
;;  :parser
;;  :query
;;  :ring/request
;;  :target
;;  )
