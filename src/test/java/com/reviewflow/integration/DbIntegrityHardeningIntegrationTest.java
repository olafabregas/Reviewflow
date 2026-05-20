package com.reviewflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.flyway.validate-on-migrate=false",
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
    })
class DbIntegrityHardeningIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private com.reviewflow.evaluation.repository.EvaluationRepository evaluationRepository;
  @Autowired private com.reviewflow.submission.repository.SubmissionRepository submissionRepository;
  @Autowired private com.reviewflow.grading.repository.InstructorScoreRepository instructorScoreRepository;
  @Autowired private com.reviewflow.grading.repository.RubricScoreRepository rubricScoreRepository;
  @Autowired private com.reviewflow.extension.repository.ExtensionRequestRepository extensionRequestRepository;

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
  @Transactional
  void evaluation_concurrentUpdate_throwsOptimisticLockingFailure() {
    Long id =
        jdbcTemplate.queryForObject("SELECT id FROM evaluations ORDER BY id ASC LIMIT 1", Long.class);
    var first = evaluationRepository.findById(id).orElseThrow();
    var second = evaluationRepository.findById(id).orElseThrow();
    first.setTotalScore(BigDecimal.valueOf(80));
    evaluationRepository.saveAndFlush(first);
    second.setTotalScore(BigDecimal.valueOf(90));
    assertThrows(OptimisticLockingFailureException.class, () -> evaluationRepository.saveAndFlush(second));
  }

  @Test
  @Transactional
  void submission_concurrentUpdate_throwsOptimisticLockingFailure() {
    Long id =
        jdbcTemplate.queryForObject("SELECT id FROM submissions ORDER BY id ASC LIMIT 1", Long.class);
    var first = submissionRepository.findById(id).orElseThrow();
    var second = submissionRepository.findById(id).orElseThrow();
    first.setChangeNote("first");
    submissionRepository.saveAndFlush(first);
    second.setChangeNote("second");
    assertThrows(OptimisticLockingFailureException.class, () -> submissionRepository.saveAndFlush(second));
  }

  @Test
  @Transactional
  void instructorScore_concurrentUpdate_throwsOptimisticLockingFailure() {
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM instructor_scores ORDER BY id ASC LIMIT 1", Long.class);
    var first = instructorScoreRepository.findById(id).orElseThrow();
    var second = instructorScoreRepository.findById(id).orElseThrow();
    first.setComment("first");
    instructorScoreRepository.saveAndFlush(first);
    second.setComment("second");
    assertThrows(
        OptimisticLockingFailureException.class, () -> instructorScoreRepository.saveAndFlush(second));
  }

  @Test
  @Transactional
  void rubricScore_concurrentUpdate_throwsOptimisticLockingFailure() {
    Long id =
        jdbcTemplate.queryForObject("SELECT id FROM rubric_scores ORDER BY id ASC LIMIT 1", Long.class);
    var first = rubricScoreRepository.findById(id).orElseThrow();
    var second = rubricScoreRepository.findById(id).orElseThrow();
    first.setComment("first");
    rubricScoreRepository.saveAndFlush(first);
    second.setComment("second");
    assertThrows(OptimisticLockingFailureException.class, () -> rubricScoreRepository.saveAndFlush(second));
  }

  @Test
  @Transactional
  void extensionRequest_concurrentUpdate_throwsOptimisticLockingFailure() {
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM extension_requests ORDER BY id ASC LIMIT 1", Long.class);
    var first = extensionRequestRepository.findById(id).orElseThrow();
    var second = extensionRequestRepository.findById(id).orElseThrow();
    first.setReason("first");
    extensionRequestRepository.saveAndFlush(first);
    second.setReason("second");
    assertThrows(
        OptimisticLockingFailureException.class, () -> extensionRequestRepository.saveAndFlush(second));
  }
}
