CREATE TABLE task_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    before_value VARCHAR(1000) NULL,
    after_value VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_task_activity_task_created (task_id, created_at, id),
    CONSTRAINT fk_task_activity_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_activity_actor FOREIGN KEY (actor_id) REFERENCES account(id)
);
