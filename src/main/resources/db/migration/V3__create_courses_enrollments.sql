CREATE TABLE courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    term VARCHAR(100),
    description TEXT,
    is_archived BOOLEAN DEFAULT FALSE,
    created_by BIGINT,
    created_at DATETIME(6),
    CONSTRAINT fk_courses_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE course_instructors (
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assigned_at DATETIME(6),
    PRIMARY KEY (course_id, user_id),
    CONSTRAINT fk_course_instructors_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_instructors_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE course_enrollments (
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    enrolled_at DATETIME(6),
    PRIMARY KEY (course_id, user_id),
    CONSTRAINT fk_course_enrollments_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_enrollments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_courses_created_by ON courses(created_by);
CREATE INDEX idx_course_instructors_user_id ON course_instructors(user_id);
CREATE INDEX idx_course_enrollments_user_id ON course_enrollments(user_id);
