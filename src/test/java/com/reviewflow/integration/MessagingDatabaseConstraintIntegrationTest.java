package com.reviewflow.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.flyway.validate-on-migrate=false",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
    })
class MessagingDatabaseConstraintIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @Transactional
  void directConversationWithTeamIdViolatesCheckConstraint() {
    Long courseId =
        jdbcTemplate.queryForObject("SELECT id FROM courses ORDER BY id ASC LIMIT 1", Long.class);
    Long teamId =
        jdbcTemplate.queryForObject("SELECT id FROM teams ORDER BY id ASC LIMIT 1", Long.class);

    assertThrows(
        UncategorizedSQLException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO conversations (course_id, conversation_type, team_id, created_at)"
                    + " VALUES (?, 'DIRECT', ?, NOW(6))",
                courseId,
                teamId));
  }

  @Test
  @Transactional
  void teamChatWithoutTeamIdViolatesCheckConstraint() {
    Long courseId =
        jdbcTemplate.queryForObject("SELECT id FROM courses ORDER BY id ASC LIMIT 1", Long.class);

    assertThrows(
        UncategorizedSQLException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO conversations (course_id, conversation_type, team_id, created_at)"
                    + " VALUES (?, 'TEAM_CHAT', NULL, NOW(6))",
                courseId));
  }
}
