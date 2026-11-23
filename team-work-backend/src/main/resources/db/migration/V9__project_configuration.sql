CREATE TABLE project_view (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    config_json JSON NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES account(id) ON DELETE CASCADE
);
CREATE TABLE project_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    field_type VARCHAR(16) NOT NULL,
    options_json JSON NOT NULL,
    is_required BOOLEAN NOT NULL,
    is_visible BOOLEAN NOT NULL,
    sort_order INT NOT NULL,
    UNIQUE (project_id, name),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);
CREATE TABLE task_field_value (
    task_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    field_value VARCHAR(2000) NOT NULL,
    PRIMARY KEY (task_id, field_id),
    FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    FOREIGN KEY (field_id) REFERENCES project_field(id) ON DELETE CASCADE
);
ALTER TABLE project ADD COLUMN template_id BIGINT, ADD COLUMN template_version INT;
