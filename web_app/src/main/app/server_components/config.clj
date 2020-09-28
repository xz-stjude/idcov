(ns app.server-components.config
  (:require
   [mount.core :as mount :refer [defstate]]
   [com.fulcrologic.fulcro.server.config :as fsc]
   [taoensso.encore :as enc]
   [taoensso.timbre :as log]
   [taoensso.timbre.appenders.core :as appenders]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [me.raynes.fs :as fs]
   [app.util :as util]))

(defn testy-getenv
  [n]
  (or (System/getenv n)
      (throw (ex-info "A required environment variable is not defined." {:var n}))))

(def default-config
  {:legal-origins #{"product.domain" "localhost"}

   :basepath             (testy-getenv "BASEPATH")
   :workflow-path        (testy-getenv "WORKFLOW_PATH")
   :refs-path            (testy-getenv "REFS_PATH")
   :example-project-path (testy-getenv "EXAMPLE_PROJECT_PATH")
   :js-main-url          "/js/main/main.js"

   :org.httpkit.server/config {:port     (Integer/parseInt (testy-getenv "PORT"))
                               :max-body 2147483647}

   ;; The ssl-redirect defaulted to off, but for security should probably be on in production.
   :ring.middleware/defaults-config {:params    {:keywordize true
                                                 :multipart  true
                                                 :nested     true
                                                 :urlencoded true}
                                     :cookies   true
                                     :responses {:absolute-redirects     true
                                                 :content-types          true
                                                 :default-charset        "utf-8"
                                                 :not-modified-responses true}
                                     :static    {:resources "public"}
                                     :session   true
                                     :proxy     false ; should be true in production with SSL enabled
                                     :security  {:anti-forgery   true
                                                 :hsts           true
                                                 :ssl-redirect   false ; should be true in production with SSL enabled
                                                 :frame-options  :sameorigin
                                                 :xss-protection {:enable? true
                                                                  :mode    :block}}}})

(defn compute-paths
  [config-map debug?]
  (merge config-map
         (let [basepath (:basepath config-map)]
           {:db-location    (util/ensure-dirs (.getPath (io/file basepath "db")))
            :file-base-path (util/ensure-dirs (.getPath (io/file basepath "files")))
            :run-base-path  (util/ensure-dirs (.getPath (io/file basepath "runs")))
            :cache-path     (util/ensure-dirs (.getPath (io/file basepath "cache")))})))

(defn simple-output-fn
  [data]
  (let [{:keys [level ?err msg_ ?ns-str ?file hostname_ timestamp_ ?line]} data]
    (str
      ;; (force timestamp_)
      ;; " "
      ;; (force hostname_)        " "
      (get {:debug "∙"
            :info  "│"
            :warn  "┽"
            :error "╳"
            :fatal "█"}
           level)  " "
      "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
      (force msg_)
      (when-let [err ?err]
        (str enc/system-newline (log/stacktrace err))))))

;; taoensso.timbre needs to configured effectfully
(defn configure-logging!
  [debug?]
  (log/merge-config! {:min-level (if debug? :debug :info)
                      :output-fn simple-output-fn
                      :appenders {:spit (appenders/spit-appender {:fname (testy-getenv "LOG_PATH")})}}))


(defn merge-dev-config
  [config-map debug?]
  (if debug?
    (do
      (log/info "Debug config is merged.")
      (merge config-map
             {:taoensso.timbre/logging-config
              {:level :debug}

              :js-main-url
              "/js_dev/main/main.js"}))
    config-map))


(defstate config
  :start (let [{:keys [debug?]} (mount/args)]
           ;; logging is so fundamental and its configuration so stateful and non-data that we need to
           ;; configure it before anything else
           (configure-logging! debug?)

           (-> default-config
               (compute-paths debug?)
               (merge-dev-config debug?))))

(comment
  (mount/start #'config)

  (log/debug "afdasfdf")
  )
