(ns libekorma.entities
  (:use [korma.core]))

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
