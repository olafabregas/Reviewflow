CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100),
    target_id BIGINT,
    metadata JSON,
    ip_address VARCHAR(45),
    created_at DATETIME(6),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_log_actor_created ON audit_log(actor_id, created_at);
