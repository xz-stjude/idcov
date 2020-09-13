(ns app.server-components.middleware
  (:require
   [app.server-components.config :refer [config]]
   [app.server-components.pathom :refer [parser]]
   [app.server-components.session-store :refer [session-store]]
   [mount.core :refer [defstate]]
   [com.fulcrologic.fulcro.server.api-middleware :as fsm]
   [com.fulcrologic.fulcro.networking.file-upload :as fu]
   [ring.middleware.defaults :as rd]
   [ring.middleware.gzip :as gzip]
   [ring.util.response :as resp]
   [ring.util.time :as rt]
   [hiccup.page :as hp]
   [taoensso.timbre :as log]
   [app.model.file :as file]
   [app.model.run :as run]
   [app.model.run :as run]
   [clojure.contrib.humanize :as humanize]
   [clojure.java.io :as io]
   [app.util :as util]
   [clojure.string :as str]))

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
  (hp/html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "Application"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/normalize/8.0.1/normalize.min.css" :rel "stylesheet"}]
      [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css" :rel "stylesheet"}]
      [:link {:href "/css/main.css" :rel "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     [:body
      [:div#app]
      [:script {:src (str "/" (if goog.DEBUG "js-dev" "js") "/main/main.js")}]]]))

(defn wrap-html-routes [ring-handler]
  ;; TODO: authentication
  (fn [{:keys [uri anti-forgery-token] :as req}]
    (cond
      ;; (#{"/" "/index.html"} uri)
      ;; (-> (resp/response (index anti-forgery-token))
      ;;     (resp/content-type "text/html"))

      :else
      (-> (resp/response (index anti-forgery-token))
          (resp/content-type "text/html")))))

(defn wrap-download-file [ring-handler]
  ;; TODO: authentication
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
          (-> (resp/file-response (.getPath file))
              (resp/header "Content-Disposition" (str "attachment; filename=\"" filename "\""))))

        :else
        (ring-handler req)))))

(defn wrap-run-output-files [ring-handler]
  (fn [{:keys [uri anti-forgery-token] :as req}]
    (let [m (re-matches #"/runs/(.*)/output-files/(.*)?" uri)]
      (cond
        (some? m)
        (let [run-id  (util/uuid (get m 1))
              rel-jf  (io/file (get m 2))
              root-jf (io/file (run/get-run-path run-id) "output_files")
              full-jf (io/file root-jf rel-jf)]
          (log/info "Serving " full-jf "...")
          (or
            (resp/file-response (.getPath rel-jf) {:root            (.getPath root-jf)
                                                   :index-files?    false
                                                   :allow-symlinks? true})
            (when (.isDirectory full-jf)
              (-> (resp/response (hp/html5
                                   [:html {:lang "en"}
                                    [:head {:lang "en"}
                                     [:title (format "Index of %s" rel-jf)]
                                     [:meta {:charset "utf-8"}]
                                     [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
                                     [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
                                     [:script (str "var fulcro_network_csrf_token = '" anti-forgery-token "';")]
                                     [:style (str/join "\n"
                                                       ["th { text-align: left; border-bottom: 1px solid #000 }"
                                                        "footer { opacity: 0.5; font-size: 0.8em }"])]]
                                    [:body
                                     [:table
                                      [:thead
                                       [:tr
                                        [:th "Name"]
                                        [:th "Size"]]]
                                      [:tbody
                                       [:tr
                                        [:td [:a {:href ".."} ".."]]
                                        [:td ""]]
                                       (for [jf (->> (.listFiles full-jf)
                                                     (sort-by #(.getName %)))]
                                         [:tr
                                          [:td (let [filename (.getName jf)]
                                                 [:a {:href filename} filename])]
                                          [:td (humanize/filesize (.length jf))]])]]
                                     [:footer
                                      [:div (format "Run: %s" run-id)]
                                      [:div (format "Path: ./%s" rel-jf)]]
                                     ]]))
                  (resp/content-type "text/html")))))

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
      (rd/wrap-defaults (assoc-in defaults-config [:session] {:store session-store}))
      gzip/wrap-gzip)))
