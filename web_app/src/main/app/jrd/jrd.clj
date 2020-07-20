(ns app.jrd.jrd
  (:require [mount.core :as mount :refer [defstate]]
            [app.model.mock-database :refer [conn]]
            [datahike.api :as d]
            [clojure.java.io :as io]
            [clojure.core.async :as async :refer [<! >! <!! >!!]]
            [taoensso.timbre :as log]
            [clojure.java.shell :as shell]
            [app.model.file :as file]
            [io.aviso.exception :as aviso-ex]
            [me.raynes.conch :refer [programs with-programs let-programs] :as sh]
            [app.model.run :as run]))


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
          (loop [chunks (lazy-cat (sh/execute "nextflow" "run"
                                              "-ansi-log" "false"
                                              (.getPath (io/resource "scripts/test.nf"))
                                              {:seq    true
                                               :buffer 4096
                                               :dir    pwd})
                                  (sh/execute "tar" "-zcv" "--dereference"
                                              "-f" "results.tar.gz"
                                              "results"
                                              {:seq    true
                                               :buffer 4096
                                               :dir    pwd}))
                 stdout ""]
            (d/transact conn [{:run/id      run-id
                               :run/status  :running
                               :run/message stdout}])
            (when (some? chunks)
              (recur (next chunks) (str stdout (first chunks)))))
          ;; ------------------------------------------------------------------------------
          ;; end work

          (d/transact conn [{:run/id     run-id
                             :run/status :succeeded}])

          (catch Exception e
            (d/transact conn [{:run/id      run-id
                               :run/status  :failed
                               :run/message (binding [aviso-ex/*fonts* nil] (aviso-ex/format-exception e))}]))))))

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

  )
