ALTER TABLE stage
    DROP FOREIGN KEY fk_stage_project,
    ADD CONSTRAINT fk_stage_project_cascade FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE;

ALTER TABLE task
    DROP FOREIGN KEY fk_task_project,
    ADD CONSTRAINT fk_task_project_cascade FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE;

ALTER TABLE task
    DROP FOREIGN KEY fk_task_stage,
    ADD CONSTRAINT fk_task_stage_cascade FOREIGN KEY (stage_id) REFERENCES stage (id) ON DELETE CASCADE;

ALTER TABLE task_comment
    DROP FOREIGN KEY fk_task_comment_task,
    ADD CONSTRAINT fk_task_comment_task_cascade FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE;

ALTER TABLE notification
    DROP FOREIGN KEY fk_notification_task,
    ADD CONSTRAINT fk_notification_task_cascade FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE;
