CREATE TABLE task_time_entry (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 task_id BIGINT NOT NULL,
 account_id BIGINT NOT NULL,
 work_date DATE NOT NULL,
 minutes SMALLINT NOT NULL,
 note VARCHAR(500) NOT NULL,
 FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
 FOREIGN KEY (account_id) REFERENCES account(id),
 INDEX (task_id, work_date)
);
CREATE TABLE schedule_baseline (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 project_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);
CREATE TABLE schedule_baseline_task (
 baseline_id BIGINT NOT NULL,
 task_id BIGINT NOT NULL,
 title TEXT NOT NULL,
 start_date DATE NULL,
 due_date DATE NULL,
 PRIMARY KEY (baseline_id, task_id),
 FOREIGN KEY (baseline_id) REFERENCES schedule_baseline(id) ON DELETE CASCADE
);
CREATE TABLE notification_webhook_preference (
 account_id BIGINT PRIMARY KEY,
 FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE
);
CREATE TABLE notification_webhook_delivery (
 id CHAR(36) PRIMARY KEY,
 account_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 next_attempt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE,
 INDEX (status, next_attempt)
);
