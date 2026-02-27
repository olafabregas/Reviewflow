CREATE TABLE evaluations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    instructor_id BIGINT NOT NULL,
    overall_comment TEXT,
    total_score DECIMAL(6,2),
    is_draft BOOLEAN DEFAULT TRUE,
    published_at DATETIME(6),
    pdf_path VARCHAR(500),
    last_edited_at DATETIME(6),
    created_at DATETIME(6),
    CONSTRAINT uk_evaluations_submission UNIQUE (submission_id),
    CONSTRAINT fk_evaluations_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_evaluations_instructor FOREIGN KEY (instructor_id) REFERENCES users(id)
);

CREATE TABLE rubric_scores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evaluation_id BIGINT NOT NULL,
    criterion_id BIGINT NOT NULL,
    score DECIMAL(5,2),
    comment TEXT,
    CONSTRAINT uk_rubric_scores_eval_criterion UNIQUE (evaluation_id, criterion_id),
    CONSTRAINT fk_rubric_scores_evaluation FOREIGN KEY (evaluation_id) REFERENCES evaluations(id) ON DELETE CASCADE,
    CONSTRAINT fk_rubric_scores_criterion FOREIGN KEY (criterion_id) REFERENCES rubric_criteria(id) ON DELETE CASCADE
);

CREATE INDEX idx_evaluations_instructor ON evaluations(instructor_id);
CREATE INDEX idx_rubric_scores_evaluation ON rubric_scores(evaluation_id);
