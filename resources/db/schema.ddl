DROP TABLE TASK_TAG IF EXISTS;
DROP TABLE TAG IF EXISTS;
DROP TABLE TASK IF EXISTS;

CREATE TABLE TASK (
  id            INT AUTO_INCREMENT NOT NULL PRIMARY KEY,
  title         VARCHAR(255)       NOT NULL,
  description   VARCHAR(1000),
  is_done       BOOLEAN            NOT NULL DEFAULT false,
  is_cancelled  BOOLEAN            NOT NULL DEFAULT false,
  created_time  TIMESTAMP          NOT NULL DEFAULT NOW(),
  finished_time TIMESTAMP
);

CREATE TABLE TAG (
  id   INT AUTO_INCREMENT PRIMARY KEY,
  name CHARACTER VARYING(50) NOT NULL
);

CREATE TABLE TASK_TAG (
  task_id INT NOT NULL REFERENCES TASK(id),
  tag_id  INT NOT NULL REFERENCES TAG(id),
  PRIMARY KEY (task_id,tag_id)
);

INSERT INTO TAG (name) VALUES ('BFPG'),('Work');

INSERT INTO TASK (title,description) VALUES
('Finish clojure lightning talk','Prepare a lightning talk on writing rest apis with clojure.'),
('Finish monocle lightning talk','Prepare a lightning talk on monocle.'),
('Buy more coffee beans','')
;

INSERT INTO TASK_TAG (task_id,tag_id) VALUES
(1,1),
(2,1),
(3,2)
;
