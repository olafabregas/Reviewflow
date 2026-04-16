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
class AssignmentGroupDatabaseConstraintIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void directInsert_weightAbove100_isRejectedByDatabase() {
        Long courseId = jdbcTemplate.queryForObject("SELECT id FROM courses ORDER BY id ASC LIMIT 1", Long.class);
        Long creatorId = jdbcTemplate.queryForObject("SELECT id FROM users ORDER BY id ASC LIMIT 1", Long.class);

        UncategorizedSQLException thrown = assertThrows(UncategorizedSQLException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO assignment_groups (course_id, name, weight, drop_lowest_n, display_order, is_uncategorized, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))",
                        courseId,
                        "Invalid Weight Group",
                        new BigDecimal("100.01"),
                        0,
                        1,
                        false,
                        creatorId
                ));

        assertDoesNotThrow(() -> thrown.getMessage());
    }

    @Test
    @Transactional
    void directInsert_negativeDropLowest_isRejectedByDatabase() {
        Long courseId = jdbcTemplate.queryForObject("SELECT id FROM courses ORDER BY id ASC LIMIT 1", Long.class);
        Long creatorId = jdbcTemplate.queryForObject("SELECT id FROM users ORDER BY id ASC LIMIT 1", Long.class);

        UncategorizedSQLException thrown = assertThrows(UncategorizedSQLException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO assignment_groups (course_id, name, weight, drop_lowest_n, display_order, is_uncategorized, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))",
                        courseId,
                        "Invalid Drop Group",
                        new BigDecimal("10.00"),
                        -1,
                        1,
                        false,
                        creatorId
                ));

        assertDoesNotThrow(() -> thrown.getMessage());
    }
}