CREATE TABLE assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    due_at DATETIME(6),
    max_team_size INT DEFAULT 1,
    is_published BOOLEAN DEFAULT FALSE,
    team_lock_at DATETIME(6),
    created_at DATETIME(6),
    CONSTRAINT fk_assignments_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);

CREATE TABLE rubric_criteria (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    max_score INT NOT NULL,
    display_order INT DEFAULT 0,
    CONSTRAINT fk_rubric_criteria_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE
);

CREATE INDEX idx_assignments_course_id ON assignments(course_id);
CREATE INDEX idx_rubric_criteria_assignment_id ON rubric_criteria(assignment_id);
