-- PRD-05: assignment extension request workflow
-- Creates request records with a strict single-owner rule (team XOR student)

CREATE TABLE extension_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    team_id BIGINT NULL,
    student_id BIGINT NULL,
    requested_by BIGINT NOT NULL,
    reason TEXT NOT NULL,
    requested_due_at DATETIME NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'DENIED') NOT NULL DEFAULT 'PENDING',
    instructor_note TEXT NULL,
    responded_by BIGINT NULL,
    responded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ext_req_assignment
        FOREIGN KEY (assignment_id) REFERENCES assignments(id),
    CONSTRAINT fk_ext_req_team
        FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_ext_req_student
        FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_ext_req_requested_by
        FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT fk_ext_req_responded_by
        FOREIGN KEY (responded_by) REFERENCES users(id),
    CONSTRAINT chk_ext_req_owner
        CHECK (
            (team_id IS NOT NULL AND student_id IS NULL) OR
            (team_id IS NULL AND student_id IS NOT NULL)
        )
);

CREATE INDEX idx_ext_req_assignment_owner
    ON extension_requests(assignment_id, team_id, student_id, status);

CREATE INDEX idx_ext_req_student
    ON extension_requests(student_id, status);
