(ns libekorma.app
  (:require [compojure.core :refer [defroutes ANY]]
        [liberator.core :refer [defresource]]
        [korma.core :refer
         [select insert values where delete update set-fields with join]]
        [korma.db :refer [defdb]]
        [libekorma.util :as util]
        [libekorma.db :as db]
        [libekorma.entities :refer [task tag task_tag]]))

(defdb dbconnection db/kormaconn)

(defn tasks-request-malformed?
  [{{method :request-method} :request :as ctx}]
  (if (= :post method)
    (let [task-data (util/parse-json-body ctx)]
      (if (empty? (:title task-data))
        [true {:message "Task title missing or empty"}]
        [false {:task-data task-data}]))
    false))

(defresource tasks-r
  :available-media-types ["application/json"]
  :allowed-methods [:get :post]
  :malformed? tasks-request-malformed?
  :post!
    (fn [{task-data :task-data}]
      (let [data (into task-data {:created_time (util/cur-time)})]
        (insert task (values data))))
  :handle-ok (fn [_]
        (util/to-json
          (select task (with tag)))))

(defn task-update-request-malformed?
  [{{method :request-method} :request :as ctx}]
  (if (= :put method)
    (let [task-data (util/parse-json-body ctx)]
      (cond
        (empty? task-data)
          [true {:message "No new values specififed"}]
        (and (contains? task-data :title)
           (empty? (:title task-data)))
          [true {:message "Empty title is not allowed"}]
        true
          [false {:task-data task-data}]))
    false))

(defn update-task [{new-task :task-data old-task :task}]
  (let [just-finished?
      (or
        (and (:is_done new-task)
           (not (:is_done old-task)))
        (and (:is_cancelled new-task)
           (not (:is_cancelled old-task))))
      finished-time-dict
      (if just-finished?
        {:finished_time (util/cur-time)}
        {})
      updated
      (into finished-time-dict
        (filter
          (fn [[k _]] (#{:title :description :is_cancelled :is_done} k))
          new-task))]
    (update task
      (set-fields updated)
      (where {:id (:id old-task)}))))

(defresource one-task-r [task-id]
  :available-media-types ["application/json"]
  :allowed-methods [:get :delete :put]
  :exists?
    (fn [_]
      (if-let [task
        (first
          (select task
            (with tag)
            (where {:id task-id})))]
        [true {:task task}]
        [false {:message "Task not found"}]))
  :delete!
    (fn [{{task-id :id} :task}]
      (delete task
        (where {:id task-id})))
  :can-put-to-missing? false
  :malformed? task-update-request-malformed?
  :put! update-task
  :handle-ok
    (fn [{task :task}]
      (util/to-json task)))

(defn post-task-tags [task-id tags]
  (let [known-tags
      (if (empty? tags)
        []
        (select tag (where {:tag [in tags]})))
      known (set (map :tag known-tags))
      unknown (filter #(not (some #{%} known)) tags)]
      (do
        (delete task_tag
        (where {:id task-id}))
        (if-not (empty? unknown)
          (insert tag
            (values (map (fn [t] {:tag t}) unknown))))
        (let [created-tags
            (select tag (where {:tag [in unknown]}))
            all-tags
              (concat known-tags created-tags)]
            (if-not (empty? all-tags)
              (insert task_tag
                (values
                  (map
                    (fn [{tag-id :id}]
                      {:task.id task-id :tag.id tag-id})
                    all-tags))))))))

(defresource task-tags-r [task-id]
  :available-media-types ["application/json"]
  :allowed-methods [:post]
  :malformed?
    (fn [ctx]
      [false {:tags (:tags (util/parse-json-body ctx))}])
  :can-post-to-missing? false
  :exists?
    (fn [_]
      (if-let [task (first (select task (where {:id task-id})))]
        [true {:task task}]
        [false {:message "Task not found"}]))
  :post!
    (fn [{tags :tags}]
      (post-task-tags task-id tags)))

(defresource tag-tasks-r [tag-word]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :exists?
    (fn [_]
      (if-let [tag-item
        (first (select tag
          (where {:name tag-word})))]
        [true {:tag tag-item}]
        [false {:message "Unknown tag"}]))
  :handle-ok
    (fn [{{tag-id :id} :tag}]
      (util/to-json
        (select task
          (with tag)
          (join task_tag
            {:id :task_tag.task_id})
          (where {:task_tag.tag_id tag-id})))))


(defroutes app
  (ANY "/" [] "Hello Clojure World!")
  (ANY "/tasks" [] tasks-r)
  (ANY "/tasks/:task-id" [task-id] (one-task-r (Integer/parseInt task-id)))
  (ANY "/tasks/:task-id/tags" [task-id] (task-tags-r (Integer/parseInt task-id)))
  (ANY "/tags/:tag" [tag] (tag-tasks-r tag)))
