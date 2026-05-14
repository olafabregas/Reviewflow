-- PRD-18 messaging (logical PRD label V27; shipped as V32 — existing V27 migration in repo)

CREATE TABLE conversations (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    course_id         BIGINT      NOT NULL,
    conversation_type ENUM('DIRECT','TEAM_CHAT') NOT NULL,
    team_id           BIGINT      NULL,
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_conv_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_conv_team
        FOREIGN KEY (team_id) REFERENCES teams(id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_conv_team
        CHECK (
            (conversation_type = 'TEAM_CHAT' AND team_id IS NOT NULL) OR
            (conversation_type = 'DIRECT' AND team_id IS NULL)
        )
);

CREATE UNIQUE INDEX uk_team_conversation ON conversations (team_id);

CREATE TABLE conversation_participants (
    conversation_id       BIGINT    NOT NULL,
    user_id               BIGINT    NOT NULL,
    joined_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_read_message_id  BIGINT    NULL,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_cp_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_cp_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_cp_user ON conversation_participants(user_id);

CREATE TABLE messages (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT    NOT NULL,
    sender_id       BIGINT      NOT NULL,
    content         TEXT        NULL,
    is_deleted      BOOLEAN     NOT NULL DEFAULT false,
    sent_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    edited_at       DATETIME(6) NULL
        COMMENT 'Set on edit — shows (edited) indicator in UI. Full edit history deferred to V2.',
    PRIMARY KEY (id),
    CONSTRAINT fk_msg_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_msg_sender
        FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE INDEX idx_msg_conversation_sent
    ON messages(conversation_id, sent_at DESC);

CREATE TABLE message_attachments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    message_id      BIGINT        NOT NULL,
    file_name       VARCHAR(255)  NOT NULL,
    file_size_bytes BIGINT        NOT NULL,
    storage_path    VARCHAR(500)  NOT NULL,
    content_type    VARCHAR(100)  NOT NULL,
    uploaded_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_ma_message
        FOREIGN KEY (message_id) REFERENCES messages(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ma_message ON message_attachments(message_id);

INSERT IGNORE INTO conversations (course_id, conversation_type, team_id, created_at)
SELECT a.course_id, 'TEAM_CHAT', t.id, NOW(6)
FROM teams t
JOIN assignments a ON t.assignment_id = a.id
WHERE NOT EXISTS (
    SELECT 1 FROM conversations c WHERE c.team_id = t.id
);

INSERT IGNORE INTO conversation_participants (conversation_id, user_id, joined_at)
SELECT c.id, tm.user_id, NOW(6)
FROM conversations c
JOIN team_members tm ON tm.team_id = c.team_id AND tm.status = 'ACCEPTED'
WHERE c.conversation_type = 'TEAM_CHAT'
    AND NOT EXISTS (
        SELECT 1 FROM conversation_participants cp
        WHERE cp.conversation_id = c.id AND cp.user_id = tm.user_id
    );
