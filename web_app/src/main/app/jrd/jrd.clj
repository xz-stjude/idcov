(ns app.jrd.jrd
  (:require [taoensso.timbre    :as log]
            [mount.core         :as mount :refer [defstate]]
            [me.raynes.conch    :as sh :refer [programs with-programs let-programs]]
            [langohr.queue      :as lq]
            [langohr.core       :as rmq]
            [langohr.consumers  :as lc]
            [langohr.channel    :as lch]
            [langohr.basic      :as lb]
            [io.aviso.exception :as aviso-ex]
            [datahike.api       :as d]
            [clojure.string     :as str]
            [clojure.java.shell :as shell]
            [clojure.java.io    :as io]
            [clojure.core.async :as async :refer [<! >! <!! >!!]]
            [app.model.run      :as run]
            [app.model.mock-database :refer [conn]]
            [app.model.file     :as file]))

(defn lazy-output->str
  [s]
  (if (seq s)
    (str "\n" (str/join "\n" s))
    "--empty--"))


;; TODO: we really need RabbitMQ to support resuming after unexpected shutdowns
;; TODO: the stdout and stderr are not reported eagerly, this might be due to the incorrect use of the (sh/execute) command
;; TODO: there needs to be a way to kill/stop a running process
;; TODO: there needs to be a timing mechanism
(defn work
  []
  ;; (log/info "Trying to get the top-priority run ...")
  ;; TODO: use nested query to make save the client-side sorting
  (let [jobs (d/q '[:find ?run-id ?txInstant
                    :keys run-id txInstant
                    :where
                    [?run :run/id ?run-id ?tx]
                    [?run :run/status :initiated]
                    [?tx :db/txInstant ?txInstant]] @conn)]
    (if (empty? jobs)
      nil ;; (log/info "No job to take")
      (let [top-job (first (sort-by :txInstant (log/spy jobs)))
            run-id  (:run-id top-job)
            pwd     (run/get-run-path run-id)]
        (try
          (log/info "found job:" (str pwd))
          (d/transact conn [[:db/cas [:run/id run-id] :run/status :initiated :running]])

          ;; begin work at `pwd`
          ;; ------------------------------------------------------------------------------
          (loop [chunks (lazy-cat
                          (sh/execute (.getPath (io/resource "workflow/test.sh"))
                                      {:seq    true
                                       :buffer 4096
                                       :dir    pwd})
                          ;; (sh/execute "nextflow"
                          ;;             "-C" (.getPath (io/resource "workflow/main.config"))
                          ;;             "run"
                          ;;             "-ansi-log" "false"
                          ;;             ;; (.getPath (io/resource "scripts/idcov_nextflow/test.nf"))
                          ;;             (.getPath (io/resource "workflow/main.groovy"))
                          ;;             {:seq    true
                          ;;              :buffer 4096
                          ;;              :dir    pwd})
                          ;; (sh/execute "tar" "-zcv" "--dereference"
                          ;;             "-f" "results.tar.gz"
                          ;;             "results"
                          ;;             {:seq    true
                          ;;              :buffer 4096
                          ;;              :dir    pwd})
                          )
                 stdout ""]
            (d/transact conn [{:run/id      run-id
                               :run/status  :running
                               :run/message stdout}])
            (when (seq chunks)
              (recur (next chunks) (str stdout (first chunks)))))
          ;; ------------------------------------------------------------------------------
          ;; end work

          ;; TODO: Throw an error if necessary output-files do not exist

          ;; register all files in the pwd/output-files folder
          (let [output-files     (filter #(.isFile %) (file-seq (io/file pwd "output-files")))
                registered-files (map (fn [f] (file/register-file conn f)) output-files)]
            (d/transact conn [{:run/id           run-id
                               :run/status       :succeeded
                               :run/output-files (vec registered-files)}]))

          (catch Exception e
            (log/spy e)
            (log/spy (class e))
            (log/spy (= clojure.lang.ExceptionInfo (class e)))
            (cond
              (= clojure.lang.ExceptionInfo (class e))
              (let [edata (ex-data e)
                    msg   (str/join "\n" [
                                          "An error occurred."
                                          "------------------"
                                          (str "Exit code: " @(:exit-code edata))
                                          (str "Stdout: " (lazy-output->str (:stdout edata)))
                                          (str "Stderr: " (lazy-output->str (:stderr edata)))])]
                (log/spy msg)
                (d/transact conn [{:run/id      run-id
                                   :run/status  :failed
                                   :run/message msg}]))

              :else
              (d/transact conn [{:run/id      run-id
                                 :run/status  :failed
                                 :run/message (binding [aviso-ex/*fonts* nil] (aviso-ex/format-exception e))}])))))))

  ;; (d/transact conn [{:account}])
  )

(defstate jrd
  :start (let [exit-ch (async/chan)]
           (async/go-loop []
             (work)
             (async/alt!
               (async/timeout 1000) (recur)
               exit-ch nil))
           exit-ch)
  :stop (async/put! jrd true))

(comment
  (mount/stop #'jrd)

  (mount/start #'jrd)


  (try
    (throw (Exception. "asfsadf"))
    (catch Exception e
      (println (binding [aviso-ex/*fonts* nil] (aviso-ex/format-exception e)))))

  (log/merge-config! {:ns-blacklist ["app.jrd.jrd"]})

  (log/merge-config! {:ns-blacklist []})

  (def ^{:const true}
    default-exchange-name "")

  (defn message-handler
    [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
    (log/info "message-handler initiated")
    (Thread/sleep (rand-int 5))
    (log/info "message-handler work finished")
    (lb/ack ch delivery-tag)
    (log/info "message-handler ack'ed")
    )

  (def c (rmq/connect))
  (def ch (lch/open c))
  (def qname "langohr.examples.hello-world")

  (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))

  (lq/declare ch qname {:exclusive false :auto-delete true})

  (lc/subscribe ch qname message-handler {:auto-ack true})

  (lb/publish ch default-exchange-name qname "Hello!" {:content-type "text/plain" :type "greetings.hi"})

  (Thread/sleep 2000)

  ;; {
  ;;  :delivery-tag 1
  ;;  :redelivery?  false
  ;;  :exchange     ""
  ;;  :routing-key  "langohr.examples.hello-world"

  ;;  :app-id           nil
  ;;  :cluster-id       nil
  ;;  :content-encoding nil
  ;;  :content-type     "text/plain"
  ;;  :correlation-id   nil
  ;;  :delivery-mode    1
  ;;  :expiration       nil
  ;;  :headers          nil
  ;;  :message-id       nil
  ;;  :persistent?      false
  ;;  :priority         nil
  ;;  :reply-to         nil
  ;;  :timestamp        nil
  ;;  :type             "greetings.hi"
  ;;  :user-id          nil
  ;;  }

  ;; {
  ;;  :delivery-tag 2
  ;;  :redelivery?  false
  ;;  :exchange     ""
  ;;  :routing-key  "langohr.examples.hello-world"

  ;;  :app-id           nil
  ;;  :cluster-id       nil
  ;;  :content-encoding nil
  ;;  :content-type     "text/plain"
  ;;  :correlation-id   nil
  ;;  :delivery-mode    1
  ;;  :expiration       nil
  ;;  :headers          nil
  ;;  :message-id       nil
  ;;  :persistent?      false
  ;;  :priority         nil
  ;;  :reply-to         nil
  ;;  :timestamp        nil
  ;;  :type             "greetings.hi"
  ;;  :user-id          nil
  ;;  }

  (println "[main] Disconnecting...")

  (rmq/close ch)

  (rmq/close conn)

  (defn msg-handler-factory
    [i n]
    (fn msg-handler
      [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
      (async/go
        (let [handler-signature (apply str (concat (repeat i \space) [i] (repeat (dec (- n i)) \space)))
              delay-secs        (rand-int 5)]
          (log/info (format "%s obtained a job (expected to finish in %d seconds)" handler-signature delay-secs))
          (Thread/sleep (* 1000 delay-secs))
          (log/info (format "%s job finished in %d seconds" handler-signature delay-secs))
          (lb/ack ch delivery-tag)))))

  (let [c     (rmq/connect)
        ch    (lch/open c)
        qname "org.stjude.cheetah.jobs"]
    (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
    (lq/declare ch qname {:exclusive false :auto-delete true})
    (lb/qos ch 1)
    (doseq [i (range 10)]
      (lc/subscribe ch qname (msg-handler-factory i 10) {:auto-ack false})))

  (doseq [i (range 20)]
    (lb/publish ch default-exchange-name "org.stjude.cheetah.jobs" "Hello!" {:content-type "text/plain" :type "greetings.hi"}))

  )


;; DeliverCallback
;;   `- Delivery
;;        `- getEnvelope -> Envelope
;;                            `- getDeliveryTag -> long
;;                            `- isRedeliver -> boolean
;;                            `- getExchange -> String
;;                            `- getRoutingKey -> String
;;                            `- [ toString -> String ]
;;        `- getProperties -> AMQP.BasicProperties
;;                              `- getContentType -> String
;;                              `- getContentEncoding -> String
;;                              `- getHeaders -> Map<String, Object>
;;                              `- getDeliveryMode -> Integer
;;                              `- getPriority -> Integer
;;                              `- getCorrelationId -> String
;;                              `- getReplyTo -> String
;;                              `- getExpiration -> String
;;                              `- getMessageId -> String
;;                              `- getTimestamp -> Date
;;                              `- getType -> String
;;                              `- getUserId -> String
;;                              `- getAppId -> String
;;        `- getBody -> byte[]
