(ns app.server-components.middleware
  (:require
   [app.server-components.config :refer [config]]
   [app.server-components.pathom :refer [parser]]
   [app.server-components.session-store :refer [session-store]]
   [mount.core :refer [defstate]]
   [com.fulcrologic.fulcro.server.api-middleware :as fsm]
   [com.fulcrologic.fulcro.networking.file-upload :as fu]
   [ring.middleware.defaults :refer [wrap-defaults]]
   [ring.middleware.gzip :refer [wrap-gzip]]
   [ring.util.response :refer [response file-response resource-response header]]
   [ring.util.response :as resp]
   [hiccup.page :refer [html5]]
   [taoensso.timbre :as log]
   [app.model.file :as file]
   [app.model.run :as run]
   [clojure.java.io :as io]
   [app.util :as util]))

(def ^:private not-found-handler
  (fn [req]
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "NOPE"}))


(defn wrap-api [handler uri]
  (fn [request]
    (if (= uri (:uri request))
      (fsm/handle-api-request
        (:transit-params request)
        (fn [tx] (parser {:ring/request request} tx)))
      (handler request))))

;; ================================================================================
;; Dynamically generated HTML. We do this so we can safely embed the CSRF token
;; in a js var for use by the client.
;; ================================================================================
(defn index [csrf-token]
  (log/debug "Serving index.html")
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "Application"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
              :rel  "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     [:body
      [:div#app]
      [:script {:src "js/main/main.js"}]]]))

;; ================================================================================
;; Workspaces can be accessed via shadow's http server on http://localhost:8023/workspaces.html
;; but that will not allow full-stack fulcro cards to talk to your server. This
;; page embeds the CSRF token, and is at `/wslive.html` on your server (i.e. port 3000).
;; ================================================================================
(defn wslive [csrf-token]
  (log/debug "Serving wslive.html")
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "devcards"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
              :rel  "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     [:body
      [:div#app]
      [:script {:src "workspaces/js/main.js"}]]]))

(defn wrap-html-routes [ring-handler]
  (fn [{:keys [uri anti-forgery-token] :as req}]
    (cond
      ;; (#{"/" "/index.html"} uri)
      ;; (-> (resp/response (index anti-forgery-token))
      ;;     (resp/content-type "text/html"))

      ;; See note above on the `wslive` function.
      (#{"/wslive.html"} uri)
      (-> (resp/response (wslive anti-forgery-token))
          (resp/content-type "text/html"))

      :else
      (-> (resp/response (index anti-forgery-token))
          (resp/content-type "text/html")))))

(defn wrap-download-file [ring-handler]
  (fn [{:keys [uri anti-forgery-token] :as req}]
    (let [file-match (re-matches #"/files/(.*)/download" uri)]
      (cond
        (some? file-match)
        (let [file-id   (util/uuid (get file-match 1))
              file      (file/get-file-path file-id)
              file-meta (get (parser {} [{[:file/id file-id] [:file/id :file/name]}]) [:file/id file-id])
              filename  (:file/name file-meta)]
          (log/spy file-meta)
          (log/spy filename)

          (log/info "Serving " file "...")
          (-> (file-response (.getPath file))
              (header "Content-Disposition" (str "attachment; filename=\"" filename "\""))))

        :else
        (ring-handler req)))))

(defn wrap-run-output-files [ring-handler]
  (fn [{:keys [uri anti-forgery-token] :as req}]
    (let [m (re-matches #"/runs/(.*)/output-files/(.*)" uri)]
      (cond
        (some? m)
        (let [run-id   (util/uuid (get m 1))
              rel-path (get m 2)
              file     (io/file (run/get-run-path run-id) "output_files" rel-path)]
          (log/info "Serving " file "...")
          (file-response (.getPath file)))

        :else
        (ring-handler req)))))

(defstate middleware
  :start
  (let [defaults-config (:ring.middleware/defaults-config config)
        legal-origins   (get config :legal-origins #{"localhost"})]
    (-> ;; not-found-handler
      (wrap-html-routes identity)
      wrap-download-file
      wrap-run-output-files
      (wrap-api "/api")
      (fu/wrap-mutation-file-uploads {})
      fsm/wrap-transit-params
      fsm/wrap-transit-response
      (wrap-defaults (assoc-in defaults-config [:session] {:store session-store}))
      wrap-gzip)))
