(ns app.model.account
  (:require [datahike.api :as d]
            [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
            [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
            [taoensso.timbre :as log]
            [clojure.spec.alpha :as s]))


(defresolver account [{{output ::pc/output} ::pc/resolver-data
                       :keys                [conn]
                       :as                  env}
                      {:account/keys [id]}]
  {::pc/input  #{:account/id}
   ::pc/output [:account/id
                :account/email
                ;; TODO: the ":project/.." etc can be resolved separately
                {:account/projects [:project/id]}
                {:account/runs [:run/id]}]}
  (d/pull @conn output [:account/id id]))

;; (defresolver account-by-email [{:keys [conn] :as env} {:account/keys [email]}]
;;   {::pc/input #{:account/email}
;;    ::pc/output [:account/id]}
;;   (d/q '[:find ?i
;;          :in $ ?email
;;          :where
;;          [?e :account/id ?i]
;;          [?e :account/email ?email]])
;;   (let [tmp (d/pull @conn output [:account/email email])]
;;     (log/debug (seq tmp))
;;     tmp))

;; TODO: SECURITY: not everything should be able to see other accounts
(defresolver all-accounts [{:keys [conn]} _]
  {::pc/output [{:all-accounts [:account/id]}]}
  {:all-accounts (d/q '[:find [(pull ?e [:account/id]) ...]
                        :where [?e :account/id ?i]] @conn)})


(def resolvers [all-accounts account])

;; 

(comment
  ;; env
  ;; {:com.wsscode.pathom.parser/done-signal* #atom[false 0x72119c31],
  ;;  :com.wsscode.pathom.connect/plan-path ([:account/password app.model.account/account-by-email]),
  ;;  :com.wsscode.pathom.parser/key-watchers #atom[{} 0x7baefd0a],
  ;;  :com.wsscode.pathom.core/plugins [...],
  ;;  :com.wsscode.pathom.core/root-query [{[:account/email "zhuxun2@gmail.com"] [:account/password]}],
  ;;  :com.wsscode.pathom.connect/resolver-data {:com.wsscode.pathom.connect/sym app.model.account/account-by-email,
  ;;                                             :com.wsscode.pathom.connect/input #{:account/email},
  ;;                                             :com.wsscode.pathom.connect/resolve #function[app.model.account/account-by-email--97619],
  ;;                                             :com.wsscode.pathom.connect/output [:account/email :account/password {:account/projects [:project/id :project/name {:project/stationed_workflows [:workflow/id :workflow/name]}]}]},
  ;;  :config {...},
  ;;  :com.wsscode.pathom.core/request-cache #atom[{[app.model.account/account-by-email {:account/email "zhuxun2@gmail.com"} {}] #object[clojure.core.async.impl.channels.ManyToManyChannel 0x3f8e851c "clojure.core.async.impl.channels.ManyToManyChannel@3f8e851c"]} 0x7d739319],
  ;;  :com.wsscode.pathom.parser/parallel? true,
  ;;  :com.wsscode.pathom.core/reader [#function[com.wsscode.pathom.core/map-reader] #function[com.wsscode.pathom.connect/parallel-reader] #function[com.wsscode.pathom.connect/open-ident-reader] #function[com.wsscode.pathom.core/env-placeholder-reader]],
  ;;  :ast {:type :prop,
  ;;        :dispatch-key :account/password,
  ;;        :key :account/password},
  ;;  :com.wsscode.pathom.core/errors* #atom[{} 0x11307d44],
  ;;  :com.wsscode.pathom.core/parent-join-key [:account/email "zhuxun2@gmail.com"],
  ;;  :com.wsscode.pathom.core/process-error #function[app.server-components.pathom/build-parser/fn--97682],
  ;;  :com.wsscode.pathom.connect/mutate-dispatch #function[com.wsscode.pathom.connect/mutation-dispatch-embedded],
  ;;  :com.wsscode.pathom.core/entity #atom[{:account/email "zhuxun2@gmail.com"} 0x6f3e8b47],
  ;;  :com.wsscode.pathom.core/entity-key :com.wsscode.pathom.core/entity,
  ;;  :com.wsscode.pathom.core/parent-query [:account/password],
  ;;  :parser #function[com.wsscode.pathom.parser/parallel-parser/self--17210],
  ;;  :com.wsscode.pathom.connect/indexes {...},
  ;;  :com.wsscode.pathom.core/path [[:account/email "zhuxun2@gmail.com"] :account/password],
  ;;  :com.wsscode.pathom.parser/key-process-timeout 58000,
  ;;  :com.wsscode.pathom.core/entity-path-cache #atom[{} 0x63746d07],
  ;;  :ring/request {...},
  ;;  :com.wsscode.pathom.core/plugin-actions {...},
  ;;  :com.wsscode.pathom.connect/pool-chan nil,
  ;;  :target nil,
  ;;  :com.wsscode.pathom.parser/waiting #{},
  ;;  :com.wsscode.pathom.parser/active-paths #atom[#{[] [[:account/email "zhuxun2@gmail.com"]]} 0x2fed12b],
  ;;  :com.wsscode.pathom.connect/resolver-weights #atom[{app.model.session/current-session-resolver 0.5,
  ;;                                                      com.wsscode.pathom.connect/indexes-resolver 0.5,
  ;;                                                      app.model.account/account-by-email 42.5} 0xeafe0e1],
  ;;  :com.wsscode.pathom.core/placeholder-prefixes #{">"},
  ;;  :com.wsscode.pathom.connect/resolver-dispatch #function[com.wsscode.pathom.connect/resolver-dispatch-embedded],
  ;;  :com.wsscode.pathom.core/async-request-cache-ch #object[clojure.core.async.impl.channels.ManyToManyChannel 0x1036566d "clojure.core.async.impl.channels.ManyToManyChannel@1036566d"],
  ;;  :conn #atom[...]}

  ({:project/id #uuid "ab0eddef-e271-4148-9b67-ec8a419153ae", :project/name "[FAKE] Webby's 20 COVID-19 Samples 4/17/2020", :project/stationed_workflows [{:workflow/id #uuid "6a295601-2007-40ad-bf50-e82b1e8e8578", :workflow/name "IDCOV standard workflow"}]}
   {:project/id #uuid "0b374321-b06a-4c97-8e6d-23d43f7d2445", :project/name "[FAKE] MD Anderson 172 Samples"})

  ;; ([{:account/email ekarchowski0@google.com.br, :account/password Organized}]
  ;;  [{:account/email gmaskell2@msn.com, :account/password model}]
  ;;  [{:account/email zhuxun2@gmail.com,
  ;;    :account/password asdfasdf,
  ;;    :account/projects [{:project/id #uuid "ab0eddef-e271-4148-9b67-ec8a419153ae"
  ;;                        :project/name "[FAKE] Webby's 20 COVID-19 Samples 4/17/2020", :project/stationed_workflows [{:workflow/id #uuid "6a295601-2007-40ad-bf50-e82b1e8e8578", :workflow/name IDCOV standard workflow}]} {:project/id #uuid "0b374321-b06a-4c97-8e6d-23d43f7d2445", :project/name [FAKE] MD Anderson 172 Samples}]}]
  ;;  [{:account/email alory1@wiley.com, :account/password installation}])
  ;; database schema
  [{:db/ident       :account/email
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :account/password
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/string}]

  ;; (vec query)
  [[{:account/email "ekarchowski0@google.com.br"}]
   [{:account/email "gmaskell2@msn.com"}]
   [{:account/email "hello@hello.com"}]
   [{:account/email "alory1@wiley.com"}]])
