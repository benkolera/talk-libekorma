(ns libekorma.app
  (:require
   [compojure.core :refer [defroutes ANY]]
   [liberator.core :refer [defresource]]
   [korma.core :refer
    [select insert values where delete update set-fields with join defentity
     many-to-many pk entity-fields]]
   [korma.db :refer [defdb h2]]
   [clojure.java.jdbc :as jdbc]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.java.io :refer [resource]]
   [libekorma.util :refer [parse-json-body body-as-string cur-time to-json keywordify]]
   )
  (:import [java.util Date]
           [java.sql Timestamp]))

;; This project aims to show off two things that are really handy for rapid
;; prototyping of APIs with very little fuss or boilerplate.
;;
;; Liberator - A webmachine like library that sits on top of compojure
;; KormaSQL  - A SQL generation DSL for clojure.

;; DB Configuration ------------------------------------------------------------

(def dbpath "resources/db/korma.db")
(def dbuser "sa")

(defdb dbconnection
  (h2 {:db dbpath
       :user dbuser
       :password ""
       :naming {:keys str/lower-case
                :fields str/upper-case}}))

;; Entity Definitions ----------------------------------------------------------

(declare tag)

(defentity task
  (pk :id)
  (entity-fields :id
                 :title
                 :description
                 :is_done
                 :is_cancelled
                 :created_time
                 :finished_time)
  (many-to-many tag :task_tag))

(defentity tag
  (pk :id)
  (entity-fields :id
                 :name)
  (many-to-many task :task_tag))

(defentity task_tag
  (entity-fields :task_id :tag_id))

;; Application Routes ----------------------------------------------------------

(declare tasks-r)
(declare one-task-r)

;; Tests:
;; curl localhost:3000/tasks
;; curl localhost:3000/tasks/1
;;

(defroutes app
  (ANY "/tasks" [] tasks-r)
  (ANY "/tasks/:task-id" [task-id] (one-task-r (Integer/parseInt task-id))))

;; Route Handler - /tasks ------------------------------------------------------

;; Parse the request and return a validated JSON structure for a new Task
(defn tasks-request-malformed?
  [{{method :request-method} :request :as ctx}]
  (if (= :post method)
    (let [task-data (parse-json-body ctx)]
      (if (empty? (:title task-data))
        [true {:message "Task title missing or empty"}]
        [false {:task-data task-data}]))
    false))

;; Define the resource for the list handler
(defresource tasks-r
  :available-media-types ["application/json"]
  :allowed-methods [:get :post]
  :malformed? tasks-request-malformed?
  :post!
    (fn [{task-data :task-data}]
      (let [data (into task-data {:created_time (cur-time)})]
        (insert task (values data))))
  :handle-ok (fn [_]
        (to-json
          (select task (with tag)))))

;; Route Handler - /tasks/<id> -------------------------------------------------

;; Parse the update request (could just be a fragment) and do some checks to make
;; sure it's a sensible update.
;; We should be able to put {"is_done":true} to a task resource and have the
;; task complete.
(defn task-update-request-malformed?
  [{{method :request-method} :request :as ctx}]
  (if (= :put method)
    (let [task-data (parse-json-body ctx)]
      (cond
        (empty? task-data)
          [true {:message "No new values specififed"}]
        (and (contains? task-data :title)
           (empty? (:title task-data)))
          [true {:message "Empty title is not allowed"}]
        true
          [false {:task-data task-data}]))
    false))

;; Lets do the song and dance of updating our task.
(defn update-task [{new-task :task-data old-task :task}]
  (let [
        ;; Did this update just finish the task?
        just-finished?
        (or
         (and (:is_done new-task)
              (not (:is_done old-task)))
         (and (:is_cancelled new-task)
              (not (:is_cancelled old-task))))

        ;; If it did, collect the current time in a dictionary.
        finished-time-dict
        (if just-finished?
          {:finished_time (cur-time)}
          {})

        ;; Merge the finished time and user updates together, filtering for just
        ;; the sensible ones.
        updated
        (into finished-time-dict
              (filter
               (fn [[k _]] (#{:title :description :is_cancelled :is_done} k))
               new-task))]

    ;; And then do our actual update to the database.
    (update task
      (set-fields updated)
      (where {:id (:id old-task)}))))

;; Define our route to handle a single task
(defresource one-task-r [task-id]
  :available-media-types ["application/json"]
  :allowed-methods [:get :delete :put]

  ;; This will return a 404 if it doesn't find a task
  :exists?
    (fn [_]
      (if-let [task
        (first
          (select task
            (with tag)
            (where {:id task-id})))]
        [true {:task task}]
        [false {:message "Task not found"}]))

  ;; This task is passed in from the exists handler
  :delete!
    (fn [{{task-id :id} :task}]
      (delete task
        (where {:id task-id})))

  :can-put-to-missing? false
  :malformed? task-update-request-malformed?

  ;; Update task takes the old task from exists? and the update verified from malformed?
  :put! update-task

  ;; This task is the task output from exists so we've already 404ed if there
  ;; was no task to display.
  :handle-ok
    (fn [{task :task}]
      (to-json task)))

;; DB Migration Stuff (Ignore me ) ---------------------------------------------

(def dbconf
  {:subprotocol "h2"
   :subname dbpath
   :user dbuser})

(defn migrate []
  (jdbc/db-do-commands
   dbconf
   true
   (slurp (resource "db/schema.ddl"))))
