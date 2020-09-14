(ns app.util
  #?(:cljs (:refer-clojure :exclude [uuid]))
  (:require [com.fulcrologic.guardrails.core :refer [>defn]]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]))

(defn uuid
  "Generate a UUID the same way via clj/cljs.  Without args gives random UUID. With args, builds UUID based on input (which
  is useful in tests)."
  #?(:clj ([] (java.util.UUID/randomUUID)))
  #?(:clj ([int-or-str]
           (if (int? int-or-str)
             (java.util.UUID/fromString
               (format "ffffffff-ffff-ffff-ffff-%012d" int-or-str))
             (java.util.UUID/fromString int-or-str))))

  #?(:cljs ([] (random-uuid)))
  #?(:cljs ([& args]
            (cljs.core/uuid (apply str args)))))

#?(:clj
   (defn resource-testy
     [n]
     "Like io/resource, but throws out an error when the requested resource does not exist (rather than silently returns a null)."
     (let [res (io/resource n)]
       (if (nil? res)
         (throw (ex-info "The requested resource does not exist." {:resource-name n}))
         res))))

