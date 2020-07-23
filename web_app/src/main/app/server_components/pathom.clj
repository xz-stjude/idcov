(ns app.server-components.pathom
  (:require
   [mount.core :refer [defstate]]
   [taoensso.timbre :as log]
   [com.wsscode.pathom.connect :as pc]
   [com.wsscode.pathom.core :as p]
   [com.wsscode.common.async-clj :refer [let-chan]]
   [clojure.core.async :as async]
   [app.model.account :as acct]
   [app.model.session :as session]
   [app.model.project :as project]
   [app.model.file :as file]
   [app.model.run :as run]
   [app.server-components.config :refer [config]]
   [app.model.mock-database :as db]
   [taoensso.timbre :as log]
   [clojure.pprint :as pp]))

(pc/defresolver index-explorer [env _]
  {::pc/input  #{:com.wsscode.pathom.viz.index-explorer/id}
   ::pc/output [:com.wsscode.pathom.viz.index-explorer/index]}
  {:com.wsscode.pathom.viz.index-explorer/index
   (-> (get env ::pc/indexes)
       (update ::pc/index-resolvers #(into {} (map (fn [[k v]] [k (dissoc v ::pc/resolve)])) %))
       (update ::pc/index-mutations #(into {} (map (fn [[k v]] [k (dissoc v ::pc/mutate)])) %)))})

(def all-resolvers [acct/resolvers
                    session/resolvers
                    project/resolvers
                    file/resolvers
                    run/resolvers
                    [index-explorer]])

(defn preprocess-parser-plugin
  "Helper to create a plugin that can view/modify the env/tx of a top-level request.

  f - (fn [{:keys [env tx]}] {:env new-env :tx new-tx})

  If the function returns no env or tx, then the parser will not be called (aborts the parse)"
  [f]
  {::p/wrap-parser
   (fn transform-parser-out-plugin-external [parser]
     (fn transform-parser-out-plugin-internal [env tx]
       (let [{:keys [env tx] :as req} (f {:env env :tx tx})]
         (if (and (map? env) (seq tx))
           (parser env tx)
           {}))))})

(defn log-requests [{:keys [env tx] :as req}]
  (log/debug "Pathom transaction:" (pr-str tx))
  req)

(def computed
  ;; create a handle key that will trigger an error when called
  {:trigger-error
   (fn [_]
     (throw (ex-info "Error triggered" {:foo "bar"})))})

;; a reader that just flows, until it reaches a leaf
(defn flow-reader [{:keys [query] :as env}]
  (if query
    (p/join env)
    :leaf))

(defn build-parser [db-connection]
  (let [real-parser (p/parallel-parser
                      {::p/mutate  pc/mutate-async
                       ::p/env     {::p/reader               [p/map-reader pc/parallel-reader
                                                              pc/open-ident-reader p/env-placeholder-reader]
                                    ::p/placeholder-prefixes #{">"}
                                    ::p/process-error        (fn [env error]
                                                               (log/error error)
                                                               (p/error-str error))}
                       ::p/plugins [(pc/connect-plugin {::pc/register all-resolvers})
                                    (p/env-wrap-plugin (fn [env]
                                                         ;; Here is where you can dynamically add things to the resolver/mutation
                                                         ;; environment, like the server config, database connections, etc.
                                                         (assoc env
                                                                ;; :db         @db-connection ; real datomic would use (d/db db-connection)
                                                                :conn       db-connection
                                                                :config     config)))
                                    (preprocess-parser-plugin log-requests)
                                    p/error-handler-plugin
                                    p/request-cache-plugin
                                    (p/post-process-parser-plugin p/elide-not-found)
                                    p/trace-plugin]})
        ;; NOTE: Add -Dtrace to the server JVM to enable Fulcro Inspect query performance traces to the network tab.
        ;; Understand that this makes the network responses much larger and should not be used in production.
        trace?      (not (nil? (System/getProperty "trace")))]
    (fn wrapped-parser [env tx]
      (log/spy (keys env))
      (log/spy tx)
      ;; [{:app.model.session/current-session [:session/valid? :account/name]}]
      (async/<!! (real-parser env (if trace?
                                    (conj tx :com.wsscode.pathom/trace)
                                    tx))))))

(defstate parser
  :start (build-parser db/conn))

;; 

(comment
  (real-parser env (if trace?
                     (conj tx :com.wsscode.pathom/trace)
                     tx))

  ;; the login query
  {(app.model.session/login {:username "hello@hello.com", :password "asdfasdf"})
   [:session/valid? :account/name]}

  ;; all-resolvers
  ;; [[{:sym     app.model.account/all-users-resolver,
  ;;    :resolve #function[app.model.account/all-users-resolver--91335],
  ;;    :output  [{:all-accounts [:account/id]}]}
  ;;   {:sym     app.model.account/account-resolver,
  ;;    :resolve #function[app.model.account/account-resolver--91345],
  ;;    :input   #{:account/id},
  ;;    :output  [:account/email :account/active?]}]

  ;;  [{:sym     app.model.session/current-session-resolver,
  ;;    :resolve #function[app.model.session/current-session-resolver--86253],
  ;;    :output  [#:app.model.session{:current-session [:session/valid?
  ;;                                                    :account/name]}]}
  ;;   {:sym    app.model.session/login,
  ;;    :mutate #function[app.model.session/login--86264],
  ;;    :output [:session/valid? :account/name]}
  ;;   {:sym    app.model.session/logout,
  ;;    :mutate #function[app.model.session/logout--86278],
  ;;    :output [:session/valid?]}
  ;;   {:sym    app.model.session/signup!,
  ;;    :mutate #function[app.model.session/signup!--86282],
  ;;    :output [:signup/result]}]

  ;;  {:sym     app.server-components.pathom/index-explorer,
  ;;   :resolve #function[app.server-components.pathom/index-explorer--91363],
  ;;   :input   #{:com.wsscode.pathom.viz.index-explorer/id},
  ;;   :output  [:com.wsscode.pathom.viz.index-explorer/index]}]

  ;; tx
  [{(app.model.session/login {:username "hello@hello.com", :password "asdfasdf"}) [:session/valid? :account/name]}]

  ;; request
  ;; {:ring/request {:transit-params     [{:app.model.session/current-session [:session/valid? :account/name]}]
  ;;                 :cookies            {"secret"       {:value "5bb094f5-f77a-4f0b-b53a-8b21ee0297cf"}
  ;;                                      "ring-session" {:value "3d8b867c-66cc-4be1-b928-bff0235bb35a"}}
  ;;                 :remote-addr        "192.168.1.81"
  ;;                 :params             {}
  ;;                 :headers            {"origin"          "http://192.168.1.76:3000"
  ;;                                      "host"            "192.168.1.76:3000"
  ;;                                      "user-agent"      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36"
  ;;                                      "content-type"    "application/transit+json"
  ;;                                      "cookie"          "secret=5bb094f5-f77a-4f0b-b53a-8b21ee0297cf; ring-session=3d8b867c-66cc-4be1-b928-bff0235bb35a"
  ;;                                      "content-length"  "84"
  ;;                                      "referer"         "http://192.168.1.76:3000/"
  ;;                                      "connection"      "keep-alive"
  ;;                                      "x-csrf-token"    "F86H2n/7aJXh1MXYFf2K2wKqeZa9DH2Im7nlFe9h9uHccHvmCevuZu8H7lFzuLNBRZ01RlOiQAF9e3aI"
  ;;                                      "accept"          "*/*"
  ;;                                      "accept-language" "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7"
  ;;                                      "accept-encoding" "gzip, deflate"}
  ;;                 :async-channel      #object[org.httpkit.server.AsyncChannel 0x153e8fa8 "/192.168.1.76:3000<->/192.168.1.81:51120"]
  ;;                 :server-port        3000
  ;;                 :content-length     84
  ;;                 :form-params        {}
  ;;                 :websocket?         false
  ;;                 :session/key        "3d8b867c-66cc-4be1-b928-bff0235bb35a"
  ;;                 :query-params       {}
  ;;                 :content-type       "application/transit+json"
  ;;                 :character-encoding "utf8"
  ;;                 :uri                "/api"
  ;;                 :server-name        "192.168.1.76"
  ;;                 :anti-forgery-token "F86H2n/7aJXh1MXYFf2K2wKqeZa9DH2Im7nlFe9h9uHccHvmCevuZu8H7lFzuLNBRZ01RlOiQAF9e3aI"
  ;;                 :query-string       nil
  ;;                 :body               #object[org.httpkit.BytesInputStream 0x41cb514a "BytesInputStream[len=84]"]
  ;;                 :multipart-params   {}
  ;;                 :scheme             :http
  ;;                 :request-method     :post
  ;;                 :session            {:ring.middleware.anti-forgery/anti-forgery-token "F86H2n/7aJXh1MXYFf2K2wKqeZa9DH2Im7nlFe9h9uHccHvmCevuZu8H7lFzuLNBRZ01RlOiQAF9e3aI"}}}
  )




