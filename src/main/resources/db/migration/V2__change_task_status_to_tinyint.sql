ALTER TABLE task ADD COLUMN status_new TINYINT NOT NULL DEFAULT 0 COMMENT '작업 상태 (TaskStatus enum의 code 값)';

UPDATE task SET status_new = CASE status
    WHEN 'CREATED'     THEN 0
    WHEN 'IN_PROGRESS' THEN 1
    WHEN 'COMPLETED'   THEN 2
    WHEN 'FAILED'      THEN 3
    WHEN 'CANCELLED'   THEN 4
    ELSE 0
END;

DROP INDEX idx_task_status ON task;
ALTER TABLE task DROP COLUMN status;
ALTER TABLE task CHANGE COLUMN status_new status TINYINT NOT NULL DEFAULT 0 COMMENT '작업 상태 (TaskStatus enum의 code 값)';
CREATE INDEX idx_task_status ON task (status);
