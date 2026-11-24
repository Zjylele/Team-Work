CREATE TABLE project_workflow (
    project_id BIGINT PRIMARY KEY,
    approver_id BIGINT NULL,
    assignment_reminder BOOLEAN NOT NULL,
    status_reminder BOOLEAN NOT NULL,
    due_reminder BOOLEAN NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (approver_id) REFERENCES account(id) ON DELETE SET NULL
);

CREATE TABLE approval_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    requested_at DATETIME(3) NOT NULL,
    KEY idx_approval_task (task_id, id),
    FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
);

CREATE TABLE approval_decision (
    approval_id BIGINT PRIMARY KEY,
    approved BOOLEAN NOT NULL,
    comment VARCHAR(1000) NOT NULL,
    decided_at DATETIME(3) NOT NULL,
    FOREIGN KEY (approval_id) REFERENCES approval_instance(id) ON DELETE CASCADE
);

CREATE TABLE task_automation_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    dedup_key VARCHAR(100) NOT NULL,
    delivered BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_task_action (task_id, action_type, dedup_key),
    KEY idx_pending_delivery (delivered, id),
    FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    FOREIGN KEY (recipient_id) REFERENCES account(id) ON DELETE CASCADE
);
