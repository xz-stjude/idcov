(ns app.application
  (:require [com.fulcrologic.fulcro.networking.http-remote :as net]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.components :as comp]
            [com.fulcrologic.fulcro.networking.file-upload :as fu]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [edn-query-language.core :as eql]
            [com.fulcrologic.fulcro.algorithms.transit :as ft]
            [cognitect.transit :as t]
            [taoensso.timbre :as log]))

(defn wrap-file-upload
  "copied from com.fulcrologic.fulcro.networking.file-upload/wrap-file-upload"
  ([handler]
   (wrap-file-upload handler {}))
  ([handler transit-options]
   (fn [req]
     (if (fu/has-uploads? req)
       (try
         (let [ast         (some-> req :body eql/query->ast)
               ast-to-send (update ast :children #(mapv (fn [n] (update n :params dissoc ::fu/uploads)) %))
               txn         (eql/ast->query ast-to-send)
               form        (js/FormData.)]
           (.append form "upload-transaction" (t/write (ft/writer transit-options) txn))
           (doseq [{:keys [dispatch-key params]} (:children ast)]
             (when-let [uploads (::fu/uploads params)]
               (doseq [{:file/keys [name content]} uploads]
                 (let [name-with-mutation (str dispatch-key "|" name)
                       js-value           (-> content meta :js-value)
                       content            (some-> js-value fu/js-value->uploadable-object)]
                   (.append form "files" content name-with-mutation)))))
           (-> req
               (assoc :body form :method :post)
               (update :headers dissoc "Content-Type")
               (assoc :response-type :default)
               ))
         (catch :default e
           (log/error e "Exception while converting mutation with file uploads.")
           {:body nil
            :method :post}))
       (handler req)))))

(def secured-request-middleware
  (-> identity
      net/wrap-fulcro-request
      wrap-file-upload
      ;; The CSRF token is embedded via server_components/html.clj
      (net/wrap-csrf-token (or js/fulcro_network_csrf_token "TOKEN-NOT-IN-HTML!"))))

(def response-middleware
  (-> identity
      net/wrap-fulcro-response
      ;; Below is a response intercepting logger for debugging
      ;; ((fn [handler] (fn [response]
      ;;                  (log/spy response)
      ;;                  (let [after-response (handler response)]
      ;;                    (log/spy after-response)
      ;;                    after-response))))
      ))

(defonce SPA (app/fulcro-app
               {;; This ensures your client can talk to a CSRF-protected server.
                ;; See middleware.clj to see how the token is embedded into the HTML
                :remotes {:remote (net/fulcro-http-remote
                                    {:url                 "/api"
                                     :request-middleware  secured-request-middleware
                                     :response-middleware response-middleware})}}))

(comment
  (-> SPA (::app/runtime-atom) deref ::app/indexes)

  (tran/write (tran/writer :json {:transform tran/write-meta})
              ;; (with-meta {:kaz "kar"} {:js-value app.ui.root/x})

              ^{:component 'app.ui.root/AccountProjectList,
                :queryid "app.ui.root/AccountProjectList"}
              [:account/id
               :account/email
               {:account/projects
                ^{:component 'app.ui.root/ProjectItem,
                  :queryid "app.ui.root/ProjectItem"}
                [:project/id
                 :project/name
                 {:project/stationed_workflows
                  ^{:component app.ui.root/Workflow,
                    :queryid "app.ui.root/Workflow"}
                  [:workflow/id :workflow/name]}]}]

              )
  )
