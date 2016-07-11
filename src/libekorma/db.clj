(ns libekorma.db
  (:require
   [korma.db :refer [h2]]
   [clojure.java.jdbc :as jdbc]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.io :refer [resource]]
   ))

(def dbpath "resources/db/korma.db")
(def dbuser "sa")

(def dbconf
  {:subprotocol "h2"
   :subname dbpath
   :user dbuser})

(def kormaconn
  (h2 {:db dbpath
       :user dbuser
       :password ""
       :naming {:keys str/lower-case
                :fields str/upper-case}}))

(defn migrate []
  (jdbc/db-do-commands
   dbconf
   true
   (slurp (resource "db/schema.ddl"))))
