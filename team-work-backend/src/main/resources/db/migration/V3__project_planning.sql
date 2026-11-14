CREATE TABLE project_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    CONSTRAINT fk_project_plan_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_plan_task (
    plan_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    PRIMARY KEY (plan_id, task_id),
    CONSTRAINT fk_plan_task_plan FOREIGN KEY (plan_id) REFERENCES project_plan(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_task_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
