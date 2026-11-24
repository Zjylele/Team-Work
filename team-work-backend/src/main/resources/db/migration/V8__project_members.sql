CREATE TABLE project_member (
    project_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    PRIMARY KEY(project_id, account_id),
    CONSTRAINT fk_project_member_project FOREIGN KEY(project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_member_account FOREIGN KEY(account_id) REFERENCES account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
