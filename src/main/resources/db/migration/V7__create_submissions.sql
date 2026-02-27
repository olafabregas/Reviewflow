CREATE TABLE submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    file_path VARCHAR(500),
    file_name VARCHAR(255),
    file_size_bytes BIGINT,
    change_note TEXT,
    uploaded_by BIGINT,
    uploaded_at DATETIME(6),
    is_late BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_submissions_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_submissions_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT uk_submissions_team_version UNIQUE (team_id, version_number)
);

CREATE INDEX idx_submissions_team_assignment ON submissions(team_id, assignment_id);
