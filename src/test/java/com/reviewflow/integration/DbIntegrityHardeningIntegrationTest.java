package com.reviewflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.util.UUID;
import org.hibernate.StaleStateException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.flyway.validate-on-migrate=false",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
    })
class DbIntegrityHardeningIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void lockVersionColumn_existsOnAllFiveTables() {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME IN ('submissions','evaluations','instructor_scores','rubric_scores','extension_requests')
              AND COLUMN_NAME = 'lock_version'
            """,
            Integer.class);
    assertEquals(5, count);
  }

  @Test
  void idx_conv_course_exists() {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'conversations'
              AND INDEX_NAME = 'idx_conv_course'
            """,
            Integer.class);
    assertEquals(1, count);
  }

  @Test
  void evaluation_concurrentUpdate_throwsOptimisticLockingFailure() {
    String stamp = UUID.randomUUID().toString();
    assertConcurrentOptimisticLockFailure(
        "evaluations",
        com.reviewflow.shared.domain.Evaluation.class,
        firstId("evaluations"),
        e -> e.setOverallComment("ole-1-" + stamp),
        e -> e.setOverallComment("ole-2-" + stamp));
  }

  @Test
  void submission_concurrentUpdate_throwsOptimisticLockingFailure() {
    String stamp = UUID.randomUUID().toString();
    assertConcurrentOptimisticLockFailure(
        "submissions",
        com.reviewflow.shared.domain.Submission.class,
        firstId("submissions"),
        s -> s.setChangeNote("ole-1-" + stamp),
        s -> s.setChangeNote("ole-2-" + stamp));
  }

  @Test
  void instructorScore_concurrentUpdate_throwsOptimisticLockingFailure() {
    String stamp = UUID.randomUUID().toString();
    assertConcurrentOptimisticLockFailure(
        "instructor_scores",
        com.reviewflow.shared.domain.InstructorScore.class,
        firstId("instructor_scores"),
        s -> s.setComment("ole-1-" + stamp),
        s -> s.setComment("ole-2-" + stamp));
  }

  @Test
  void rubricScore_concurrentUpdate_throwsOptimisticLockingFailure() {
    String stamp = UUID.randomUUID().toString();
    assertConcurrentOptimisticLockFailure(
        "rubric_scores",
        com.reviewflow.shared.domain.RubricScore.class,
        firstId("rubric_scores"),
        s -> s.setComment("ole-1-" + stamp),
        s -> s.setComment("ole-2-" + stamp));
  }

  @Test
  void extensionRequest_concurrentUpdate_throwsOptimisticLockingFailure() {
    String stamp = UUID.randomUUID().toString();
    assertConcurrentOptimisticLockFailure(
        "extension_requests",
        com.reviewflow.shared.domain.ExtensionRequest.class,
        firstId("extension_requests"),
        r -> r.setReason("ole-1-" + stamp),
        r -> r.setReason("ole-2-" + stamp));
  }

  private Long firstId(String table) {
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    assumeTrue(count != null && count > 0, () -> "No seed rows in " + table);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM " + table + " ORDER BY id ASC LIMIT 1", Long.class);
  }

  private Long lockVersion(String table, Long id) {
    return jdbcTemplate.queryForObject(
        "SELECT lock_version FROM " + table + " WHERE id = ?", Long.class, id);
  }

  private <T> void assertConcurrentOptimisticLockFailure(
      String table,
      Class<T> entityClass,
      Long id,
      Consumer<T> mutateFirst,
      Consumer<T> mutateSecond) {
    Long versionBefore = lockVersion(table, id);
    EntityManager em1 = entityManagerFactory.createEntityManager();
    EntityManager em2 = entityManagerFactory.createEntityManager();
    try {
      em1.getTransaction().begin();
      em2.getTransaction().begin();
      T first = em1.find(entityClass, id);
      T second = em2.find(entityClass, id);
      mutateFirst.accept(first);
      em1.getTransaction().commit();

      assertEquals(
          versionBefore + 1,
          lockVersion(table, id),
          () ->
              "First transaction must increment lock_version (mutation was likely a no-op)");

      mutateSecond.accept(second);
      assertOptimisticLockOnCommit(() -> em2.getTransaction().commit());
    } finally {
      if (em1.getTransaction().isActive()) {
        em1.getTransaction().rollback();
      }
      if (em2.getTransaction().isActive()) {
        em2.getTransaction().rollback();
      }
      em1.close();
      em2.close();
    }
  }

  private void assertOptimisticLockOnCommit(Runnable commit) {
    Exception thrown =
        assertThrows(
            Exception.class, commit::run, () -> "Expected optimistic lock failure on commit");
    assertTrue(
        isOptimisticLockFailure(thrown),
        () -> "Expected optimistic lock failure but got " + thrown);
  }

  private static boolean isOptimisticLockFailure(Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (current instanceof OptimisticLockException || current instanceof StaleStateException) {
        return true;
      }
    }
    return false;
  }
}
