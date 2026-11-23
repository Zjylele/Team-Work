ALTER TABLE task ADD COLUMN start_date DATE NULL;

CREATE TABLE task_dependency (
    predecessor_task_id BIGINT NOT NULL,
    successor_task_id BIGINT NOT NULL,
    PRIMARY KEY (predecessor_task_id, successor_task_id),
    CONSTRAINT fk_dependency_predecessor FOREIGN KEY (predecessor_task_id) REFERENCES task(id) ON DELETE CASCADE,
    CONSTRAINT fk_dependency_successor FOREIGN KEY (successor_task_id) REFERENCES task(id) ON DELETE CASCADE
);

CREATE TABLE portfolio (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    CONSTRAINT fk_portfolio_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
);

CREATE TABLE portfolio_project (
    portfolio_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    PRIMARY KEY (portfolio_id, project_id),
    CONSTRAINT fk_portfolio_project_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio(id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_project_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);
