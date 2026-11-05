CREATE TABLE account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE team (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    invite_code CHAR(5) NOT NULL,
    created_by BIGINT NOT NULL,
    single_row TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_team_invite_code (invite_code),
    UNIQUE KEY uk_team_single_row (single_row),
    CONSTRAINT fk_team_created_by FOREIGN KEY (created_by) REFERENCES account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE team_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_team_member_account (account_id),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES team (id),
    CONSTRAINT fk_team_member_account FOREIGN KEY (account_id) REFERENCES account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_project_team FOREIGN KEY (team_id) REFERENCES team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_stage_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    assignee_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_task_stage FOREIGN KEY (stage_id) REFERENCES stage (id),
    CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_id) REFERENCES account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_task_comment_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_task_comment_author FOREIGN KEY (author_id) REFERENCES account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    read_flag TINYINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_notification_recipient (recipient_id, created_at),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES account (id),
    CONSTRAINT fk_notification_task FOREIGN KEY (task_id) REFERENCES task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
