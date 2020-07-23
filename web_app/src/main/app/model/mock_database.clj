(ns app.model.mock-database
  (:require [app.server-components.config :refer [config]]
            [datahike.api :as d]
            [datahike.migrate :as dm]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as log]))


(def db-schema
  [
   ;; account
   {:db/ident       :account/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :account/email
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/value}
   {:db/ident       :account/password
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :account/projects
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}
   {:db/ident       :account/runs
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}

   ;; run
   {:db/ident       :run/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :run/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   ;; TODO: maybe change the :db.type to https://docs.datomic.com/on-prem/schema.html#enums
   ;; status can be one of
   ;; #{:initiating :initiation-failed :initiated :retracted :running
   ;;   :interrupted :stopping :failed :succeeded}
   {:db/ident       :run/status
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident       :run/message
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   ;; {:db/ident       :run/workflow
   ;;  :db/valueType   :db.type/ref
   ;;  :db/cardinality :db.cardinality/one}

   ;; project
   {:db/ident       :project/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :project/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :project/files
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}
   ;; stationed_workflows are not needed for idCOV
   ;; {:db/ident       :project/stationed_workflows
   ;;  :db/valueType   :db.type/ref
   ;;  :db/cardinality :db.cardinality/many}

   ;; workflow
   {:db/ident       :workflow/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :workflow/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   ;; file
   {:db/ident       :file/id
    :db/valueType   :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :file/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :file/size
    :db/valueType   :db.type/bigint
    :db/cardinality :db.cardinality/one}
   ])

(defn new-database []
  (let [;; note that the cfg has to be created within the new-database function
        ;; because it depends on config which is a mount/DeferredState that
        ;; needs to be initialized before this is run
        cfg {:store      {:backend :file
                          :path    (:db-location config)}
             :initial-tx db-schema}]

    ;; Create the database file if it doesn't exist
    (when (not (d/database-exists? cfg))
      (log/info (format "Database does not exist, creating ... cfg = %s" cfg))
      (d/create-database cfg)
      (log/info (format "Database created.")))

    (d/connect cfg)))

(defstate conn
  :start (new-database)
  :stop (d/release conn))


;; 


(comment
  (user/restart)

  (d/database-exists? cfg)
  (d/connect cfg)

  ;; (d/transact conn [{:db/ident       :account/email
  ;;                    :db/valueType   :db.type/string
  ;;                    :db/cardinality :db.cardinality/one
  ;;                    :db/unique      :db.unique/identity}
  ;;                   {:db/ident       :account/password
  ;;                    :db/cardinality :db.cardinality/one
  ;;                    :db/valueType   :db.type/string}])

  (d/datoms @conn :eavt)

  (let [cfg {:store {:backend :file
                     :path    (:db-location config)}}]
    (d/delete-database cfg))

  (d/transact conn [#:account{:id       #uuid "12e59b5c-93f3-48ce-9ff0-5a972ffaf41a",
                              :email    "zhuxun2@gmail.com",
                              :password "asdfasdf",
                              :projects
                              [#:project{:id    #uuid "0b374321-b06a-4c97-8e6d-23d43f7d2445",
                                         :name  "July batch"
                                         :files [#:file{:id   #uuid "c7c64b69-8887-4db6-9e27-3cd5c7fe1cdb"
                                                        :name "Rabeh-1_S7_R1.fastq.gz"
                                                        :size 2319852480}
                                                 #:file{:id   #uuid "c1da0e8c-3cf9-4154-9ee1-45e0d83dd039"
                                                        :name "Rabeh-1_S7_R2.fastq.gz"
                                                        :size 2752387258}
                                                 #:file{:id   #uuid "59917fb7-fca3-4772-b45b-344aea01fdaf"
                                                        :name "Rabeh-2_S8_R1.fastq.gz"
                                                        :size 1298479424}
                                                 #:file{:id   #uuid "6b751c08-a30e-4714-b1c6-72b1fbee5968"
                                                        :name "Rabeh-2_S8_R2.fastq.gz"
                                                        :size 1548732741}]}]}
                    #:account{:id       #uuid "16510c9e-d7f5-4ffe-a0d6-388b2efcf3a9",
                              :email    "gang.wu@stjude.org",
                              :password "WWtYFp4G6iRBsQVBS7MOu"
                              :projects
                              [#:project{:id #uuid "0b374321-b06a-4c97-8e6d-23d43f7d2445"}]}
                    #:account{:id       #uuid "61ae49a7-2922-4b69-8041-ab28663fea19",
                              :email    "alory1@wiley.com",
                              :password "installation"}
                    #:account{:id       #uuid "9b8c941d-1b85-44be-b524-991d2257aa37",
                              :email    "ekarchowski0@google.com.br",
                              :password "Organized"}])

  (d/transact conn [{:project/id    #uuid "ab0eddef-e271-4148-9b67-ec8a419153ae"
                     :project/files '({:file/id           #uuid "0afee12a-cfcb-4032-988e-479974430b11"
                                       :file/name         "bar.txt"
                                       :file/tmpfile-path "/tmp/ring-multipart-5985700082394151342.tmp"
                                       :file/tag          "foo_bar"}
                                      {:file/id           #uuid "e458201a-a878-43a7-9589-1b005c027fa9"
                                       :file/name         "foo.txt"
                                       :file/tmpfile-path "/tmp/ring-multipart-16040817707785563895.tmp"
                                       :file/tag          "foo_bar"})}])

  (d/transact conn [{:account/id       #uuid "12e59b5c-93f3-48ce-9ff0-5a972ffaf41a"
                     :account/projects [{:project/id                  #uuid "3c3ed4d0-8388-4009-8e0e-c7c81eed14e4"
                                         :project/name                "asdfasfa"
                                         :project/stationed_workflows [:workflow/id #uuid "6a295601-2007-40ad-bf50-e82b1e8e8578"]
                                         :project/files               '({:file/id           #uuid "0afee12a-cfcb-4032-988e-479974430b11"
                                                                         :file/name         "bar.txt"
                                                                         :file/tmpfile-path "/tmp/ring-multipart-5985700082394151342.tmp"
                                                                         :file/tag          "foo_bar"}
                                                                        {:file/id           #uuid "e458201a-a878-43a7-9589-1b005c027fa9"
                                                                         :file/name         "foo.txt"
                                                                         :file/tmpfile-path "/tmp/ring-multipart-16040817707785563895.tmp"
                                                                         :file/tag          "foo_bar"})}]}])

  (d/transact conn [#:account {:id       #uuid "12e59b5c-93f3-48ce-9ff0-5a972ffaf41a",
                               :email    "zhuxun2@gmail.com",
                               :password "asdfasdf",
                               :projects
                               [#:project{:id                  (java.util.UUID/randomUUID),
                                          :name                "[FAKE] Webby's 20 COVID-19 Samples 4/17/2020",
                                          :stationed_workflows [#:workflow{:id   #uuid "6a295601-2007-40ad-bf50-e82b1e8e8578",
                                                                           :name "IDCOV standard workflow"}]}]}])

  (d/transact conn [[:db/retract [:account/id #uuid "12e59b5c-93f3-48ce-9ff0-5a972ffaf41a"]
                     :account/projects [:project/id #uuid "688b15cc-7d34-443c-ab84-763a6501e555"]]])

  (d/transact conn [{:db/ident       :run/id
                     :db/valueType   :db.type/uuid
                     :db/cardinality :db.cardinality/one
                     :db/unique      :db.unique/identity}
                    {:db/ident       :run/name
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}
                    {:db/ident       :run/pwd
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}])

  (d/q '[:find [(pull ?e [:account/id
                          :account/email
                          :account/password
                          {:account/projects [:project/id
                                              :project/name
                                              {:project/files [:file/id
                                                               :file/name
                                                               :file/size]}]}]) ...]
         :where
         [?e :account/email "zhuxun2@gmail.com"]
         ;; [?e :account/id]
         ]
       @conn)

  ;; nested query to get the top items
  (d/q '[:find ?project-id ?txInstant
         ;; :keys #_project-id creation-inst
         ;; :in $ [[txInstant]]
         :where
         [(d/q [:find (min 5 ?txInstant) .
                :where
                [?project :project/id ?project-id ?tx]
                [?tx :db/txInstant ?txInstant]]
               $) [?txInstant ...]]
         [?project :project/id ?project-id ?tx]
         [?tx :db/txInstant ?txInstant]] @conn)

  (d/q '[:find (min 5 ?txInstant) .
         :where
         [?project :project/id ?project-id ?tx]
         [?tx :db/txInstant ?txInstant]]
       @conn)

  (d/q '[:find [(pull ?files [:file/id
                              :file/name
                              :file/size]) ...]
         :in $ ?id
         :where
         [?project :project/id ?id]
         [?project :project/files ?files]
         ;; [?e :account/id]
         ]
       @conn
       #uuid "0b374321-b06a-4c97-8e6d-23d43f7d2445"
       )

  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :account/id]]
       @conn)

  (d/pull @conn '[*] [:account/email "zhuxun2@gmail.com"])

  (map :name (filter #(and (contains? (:flags %) :public)
                           (not (contains? (:flags %) :static)))
                     (:members (clojure.reflect/reflect #uuid "41ab831d-d0e3-41fb-838f-f71b721bf1d1"))))

  [(d/transact conn [[:db.fn/retractEntity [:account/email "zhuxun2@gmail.com"]]])]

  (d/transact conn [[:db.fn/retractEntity 3]])

  ;; (dm/export-db @conn "/tmp/iocov-db-exported")

  (d/retract)

  (d/pull @conn [:account/email :account/password] [:account/email "zhuxun2@gmail.com"])

  (d/q '[:find (pull ?e [:account/email])
         :where [?e :account/email]]
       @conn
       )

  (d/pull @conn [:account/email :account/password] [:account/email "zhuxun2@gmail.com"])

  ;; datoms
  [#datahike/Datom  [1          :db/cardinality      :db.cardinality/one              536870913]
   #datahike/Datom  [1          :db/ident            :account/email                   536870913]
   #datahike/Datom  [1          :db/unique           :db.unique/identity              536870913]
   #datahike/Datom  [1          :db/valueType        :db.type/string                  536870913]
   #datahike/Datom  [2          :db/cardinality      :db.cardinality/one              536870913]
   #datahike/Datom  [2          :db/ident            :account/password                536870913]
   #datahike/Datom  [2          :db/valueType        :db.type/string                  536870913]
   #datahike/Datom  [536870913  :db/txInstant #inst  "2020-06-12T00:37:30.344-00:00"  536870913]

   #datahike/Datom  [3          :account/email       "zhuxun2@gmail.com"                536870914]
   #datahike/Datom  [3          :account/password    "asdfasdf"                       536870914]
   #datahike/Datom  [536870914  :db/txInstant #inst  "2020-06-12T00:39:12.805-00:00"  536870914]]

  (d/datoms (d/as-of @conn 536870914) :eavt)

  (d/q '[:find ?email ?password
         :where
         [?e :account/email ?email]
         [?e :account/password ?password]] @conn))
