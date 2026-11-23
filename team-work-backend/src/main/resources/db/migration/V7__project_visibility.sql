ALTER TABLE project ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'SHARED';
ALTER TABLE project ADD COLUMN owner_id BIGINT NULL;
UPDATE project p SET owner_id = (SELECT tm.account_id FROM team_member tm WHERE tm.team_id = p.team_id AND tm.role = 'CREATOR' LIMIT 1);
ALTER TABLE project MODIFY owner_id BIGINT NOT NULL;
ALTER TABLE project ADD CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES account(id);
