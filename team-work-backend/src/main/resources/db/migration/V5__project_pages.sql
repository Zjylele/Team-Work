CREATE TABLE project_page (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    CONSTRAINT fk_page_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);
