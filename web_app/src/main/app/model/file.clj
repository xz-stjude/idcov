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

;; TODO: rename this to "get-file" -- as it returns a file object rather than a string
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
        ;; TODO: NEXT: make :file/size always calculated from the file
        (d/transact conn [{:file/id   file-id
                           :file/size nil}])))))

(defn generate-file-id
  [file]
  ;; TODO: use the sha256 hash of the file instead (to avoid duplication)
  (util/uuid))


(defn sanitize-filename
  [filename]
  (str/replace filename #"[^a-zA-Z0-9_.\-]" "_"))


(defn register-file
  "Copies the file to the file warehouse, and then register it on the database.

  options:

      :filename        - If a string, use it as the name of the file registered on the server.
                         Otherwise, the original filename of the supplied path-to-file is used. (default: nil)

      :link-method     - One of :copy, :sym-link, and :move. (default: :sym-link)

      :project-id      - If an uuid, the registered file will be added to the project with supplied id.
                         If nil, nothing will be done. (default: nil)
  "
  ([conn path-to-file] (register-file conn path-to-file {}))
  ([conn path-to-file {:keys [filename link-method project-id]
                       :or   {filename    nil
                              link-method :sym-link
                              project-id  nil}}]
   (let [file      (io/file path-to-file)
         filesize  (.length file) ; NOTE: .length follows sym-link
         filename_ (sanitize-filename (if (string? filename)
                                        filename
                                        (.getName file)))
         file-id   (generate-file-id file)
         dest-file (get-file-path file-id)]
     (log/spy filename_)
     (log/spy file-id)
     (log/spy dest-file)
     (log/debug #:file{:id   file-id
                       :name filename_
                       :size (.length file)})
     (try

       (case link-method
         :sym-link (do
                     (fs/mkdirs (.getParentFile dest-file))
                     (fs/sym-link dest-file file))
         :copy     (fs/copy+ file dest-file)
         :move     (do
                     (fs/copy+ file dest-file)
                     (fs/delete file)))

       (d/transact conn [{:db/id     "new_file"
                          :file/id   file-id
                          :file/name filename_
                          ;; File size is stored directly into the database because
                          ;; we would like to minimize the access to the file system,
                          ;; since it is something many frontend views would ask for.
                          :file/size filesize}
                         (when (= java.util.UUID (class project-id))
                           {:project/id    project-id
                            :project/files ["new_file"]})])

       {:file/id   file-id
        :file/name filename_
        :file/size filesize}

       (catch Exception e
         (throw (ex-info "Error when trying to register a file"
                         {:filename     filename
                          :file         file
                          :path-to-file path-to-file
                          :filesize     filesize
                          :file-id      file-id
                          :dest-file    dest-file}
                         e)))))))

(defn register-uploaded-file
  [conn project-id js-file]
  ;; js-file =
  ;;     {:filename "sample01_R1.fastq"
  ;;      :content-type "xxx/xxx"
  ;;      :tempfile #object[java.io.File 0x5a7d2ed6 "/tmp/ring-multipart-443736812093768988.tmp"]
  ;;      :size 521212}
  (register-file conn (:tempfile js-file)
                 {:filename    (:filename js-file)
                  :project-id  project-id
                  :link-method :move}))

(def resolvers [file-r])

(comment
  (refresh-file-cache)

  (register-file conn "/data/1000/home/tmp/covid_samples/VIC4750_R1.fastq.gz" {:project-id #uuid "a86dc6da-321e-4afe-a8fa-be00bd30e187"})

  (doseq [f (.listFiles (io/file "/data/1000/home/tmp/covid_samples/selected"))]
    (register-file conn f {:project-id #uuid "f30c317d-16c5-4f20-9dde-c8780197680c"}))

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
