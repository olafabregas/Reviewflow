package com.reviewflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class InstructorScoreDatabaseConstraintIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void directInsert_negativeScore_isRejectedByDatabase() {
        Long assignmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM assignments ORDER BY id ASC LIMIT 1",
                Long.class);
        Long actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM users ORDER BY id ASC LIMIT 1",
                Long.class);
        Long studentId = jdbcTemplate.queryForObject(
                "SELECT id FROM users ORDER BY id ASC LIMIT 1",
                Long.class);

        UncategorizedSQLException thrown = assertThrows(UncategorizedSQLException.class, () -> jdbcTemplate.update(
                "INSERT INTO instructor_scores (assignment_id, student_id, team_id, score, max_score, comment, is_published, published_at, entered_by, entered_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))",
                assignmentId,
                studentId,
                null,
                new BigDecimal("-0.01"),
                new BigDecimal("100.00"),
                "invalid",
                false,
                null,
                actorId
        ));

        assertDoesNotThrow(thrown::getMessage);
    }

    @Test
    @Transactional
    void directInsert_scoreAboveMax_isRejectedByDatabase() {
        Long assignmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM assignments ORDER BY id ASC LIMIT 1",
                Long.class);
        Long actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM users ORDER BY id ASC LIMIT 1",
                Long.class);
        Long studentId = jdbcTemplate.queryForObject(
                "SELECT id FROM users ORDER BY id ASC LIMIT 1",
                Long.class);

        UncategorizedSQLException thrown = assertThrows(UncategorizedSQLException.class, () -> jdbcTemplate.update(
                "INSERT INTO instructor_scores (assignment_id, student_id, team_id, score, max_score, comment, is_published, published_at, entered_by, entered_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))",
                assignmentId,
                studentId,
                null,
                new BigDecimal("100.01"),
                new BigDecimal("100.00"),
                "invalid",
                false,
                null,
                actorId
        ));

        assertDoesNotThrow(thrown::getMessage);
    }
}
