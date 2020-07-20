(ns app.model.file
  "The main job of this ns is to make sure that the database :file/id entries are in-sync with what's actually
  in the file storage folder. That's why all functions in this ns always do things on both the database and the file system.
  We are essentially using the file system as the database for files."
  (:require [clojure.java.io :as io]
            [app.util :as util]
            [app.server-components.config :as config]
            [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
            [taoensso.timbre :as log]
            [me.raynes.fs :as fs]
            [app.model.mock-database :refer [conn]]
            [clojure.string :as str]
            [datahike.api :as d]))

(defn get-file-path
  "Get or generate the file path given file-id
  The first two chars of the uuid are extracted as an extra level of directory, e.g.,
  '05/989875-be50-4496-b78b-db641891b4f5'. This is to avoid a flat folder structure
  resulting in too many files in the base folder, which causes a degraded performance
  in Linux."
  [file-id]
  (let [base-path   (:file-base-path config/config)
        file-id-str (str file-id)
        part-1      (subs file-id-str 0 2)
        part-2      (subs file-id-str 2)]
    (io/file base-path part-1 part-2)))

(defresolver file-r [{{output ::pc/output} ::pc/resolver-data
                      :keys                [conn]
                      :as                  env}
                     {:file/keys [id]}]
  {::pc/input  #{:file/id}
   ::pc/output [:file/id
                :file/name
                :file/size]}
  (log/spy output)
  (log/spy (::pc/resolver-data env))
  (d/pull @conn output [:file/id id]))

(defn refresh-file-cache
  "Recalculate file sizes, and mark non-existing files as having size nil."
  []
  (doseq [file (d/q '[:find [(pull ?file [:file/id]) ...]
                      :where [?file :file/id]] @conn)]
    (let [file-id      (:file/id file)
          file         (get-file-path file-id)
          file-exists? (.exists file)]
      (if file-exists?
        (d/transact conn [{:file/id   file-id
                           :file/size (.length file)}])
        (d/transact conn [{:file/id   file-id
                           :file/size nil}])))))

(defn store-files-and-add-them-to-project
  "This function does the following to a list of files

  NOTE: files with the same filename will all be stored side-by-side without overwritting each other.

      * Each file will be assigned a UUID.
      * The file will be moved from the temporary folder into the storage folder
      * The relation [:project/id :project/files :file/id] will be written to the database"
  [conn project-id files]
  (let [base-path (:file-base-path config/config)]
    (log/spy base-path)
    (doseq [file files]
      ;; file =
      ;;     {:filename "sample01_R1.fastq"
      ;;      :content-type "xxx/xxx"
      ;;      :tempfile #object[java.io.File 0x5a7d2ed6 "/tmp/ring-multipart-443736812093768988.tmp"]
      ;;      :size 521212}
      (let [;; TODO: use the sha256 hash of the file instead (to avoid duplication)
            file-id   (util/uuid)
            dest-file (get-file-path file-id)]
        (fs/copy+ (:tempfile file) dest-file)
        (fs/delete (:tempfile file))
        (d/transact conn [{:project/id    project-id
                           :project/files [{:file/id   file-id
                                            :file/name (:filename file)
                                            ;; File size is stored directly into the database because
                                            ;; we would like to minimize the access to the file system,
                                            ;; since it is something many frontend views would ask for.
                                            :file/size (:size file)}]}])))))

(def resolvers [file-r])

(comment
  (refresh-file-cache)

  )

;; (file/r-files-by-account)
;;   there needs to be a resolver: :project/id -> :project/files


;; ((fn [f] {:filename (.getName f)
;;           :size (.length f)})
;;  (io/file "/bin/sh"))

;; (#(. (io/file "./account.clj") %) getName)

;; (map #(% 12) [inc dec])

;; (defn mapkv [f m]
;;   (into {}
;;         (map #(vector (key %)
;;                       (f (val %)))
;;              m)))

;; (class (first (seq {:foo "bar"})))
