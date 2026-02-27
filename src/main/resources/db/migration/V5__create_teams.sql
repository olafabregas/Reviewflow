CREATE TABLE teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_locked BOOLEAN DEFAULT FALSE,
    created_by BIGINT,
    created_at DATETIME(6),
    CONSTRAINT fk_teams_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_teams_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE team_members (
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    joined_at DATETIME(6),
    invited_by BIGINT,
    status ENUM('PENDING', 'ACCEPTED', 'DECLINED') DEFAULT 'PENDING',
    PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_invited_by FOREIGN KEY (invited_by) REFERENCES users(id),
    CONSTRAINT fk_team_members_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id),
    CONSTRAINT uk_team_members_assignment_user UNIQUE (assignment_id, user_id)
);

CREATE INDEX idx_teams_assignment_id ON teams(assignment_id);
CREATE INDEX idx_team_members_user_id ON team_members(user_id);
