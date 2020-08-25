(ns app.jrd.jrd
  (:require [taoensso.timbre    :as log]
            [mount.core         :as mount :refer [defstate]]
            [me.raynes.conch    :as sh :refer [programs with-programs let-programs]]
            [me.raynes.conch.low-level :as cl]
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

;; But here is the deal. RabbitMQ by itself cannot fully solve stopping and
;; resuming of a job. To support resuming, we still need a database for storing
;; the status of each job, and each worker would still need to check this
;; database to see if a job has already been canceled before they proceed to
;; start the job.

;; What RabbitMQ solves is really just the subscribing so that we don't have to
;; do probing.

;; Even if we use RabbitMQ, and there's an unexpected server shutdown, when we
;; restart the server again, we would still need to do a sweeping of the jobs
;; marked as "running".


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
          (let [p (cl/proc (.getPath (io/resource "workflow/test.sh")) :dir pwd)]
            (cl/stream-to p :out (io/file pwd "stdout"))
            (cl/stream-to p :err (io/file pwd "stderr")))
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


;; 

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

  (def p (cl/proc (.getPath (io/resource "workflow/test_echoes.sh"))))
  (cl/stream-to p :out (io/file "/tmp/asdf"))


  (do
    (log/info ".isAlive() =" (.isAlive (:process p)))
    (log/info "stdout =\n" (cl/stream-to-string p :out)))

  (do
    (def pb (ProcessBuilder. ["echo" "Hello!"]))

    (def p (.start pb))
    (-> p .getInputStream .read)
    (-> p .getInputStream .available)
    )


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

  (do
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

    (def qname "idcov.jobs")
    (def rmq-conn (rmq/connect))
    (def ch-pub (lch/open rmq-conn))
    (def ch-sub (lch/open rmq-conn))
    )

  (do
    (println (format "[consumers] Connected. Channel id: %d" (.getChannelNumber ch-sub)))
    (lq/declare ch-sub qname {:exclusive false :auto-delete true})
    (lb/qos ch-sub 0)

    ;; This part will appear in this file (jrd). Instead of listening to a database, JRDs now subscribe to the idcov.jobs queue in the RabbitMQ server
    (doseq [i (range 10)]
      (lc/subscribe ch-sub qname (msg-handler-factory i 10) {:auto-ack false}))

    (println (format "[producer] Connected. Channel id: %d" (.getChannelNumber ch-pub)))
    (lq/declare ch-pub qname {:exclusive false :auto-delete true})
    (lb/qos ch-pub 0)

    ;; This part will appear at the "POST /submit-jobs" handler
    (doseq [i (range 20)]
      (lb/publish ch-pub "" qname "Hello!" {:content-type "text/plain" :type "greetings.hi"}))
    )

  (do
    (rmq/close ch-pub)
    (rmq/close ch-sub)
    (rmq/close rmq-conn))

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
