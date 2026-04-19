-- V22__create_assignment_modules.sql

CREATE TABLE assignment_modules (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    course_id     BIGINT        NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    display_order INT           NOT NULL DEFAULT 0,
    created_by    BIGINT        NOT NULL,
    created_at    DATETIME      NOT NULL DEFAULT NOW(),
    updated_at    DATETIME      NOT NULL DEFAULT NOW()
                                ON UPDATE NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_am_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_am_creator
        FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX idx_am_course ON assignment_modules(course_id, display_order);

ALTER TABLE assignments
    ADD COLUMN module_id BIGINT NULL,
    ADD CONSTRAINT fk_assignment_module
        FOREIGN KEY (module_id) REFERENCES assignment_modules(id)
        ON DELETE SET NULL;

