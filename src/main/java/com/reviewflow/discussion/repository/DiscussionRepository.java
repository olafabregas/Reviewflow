package com.reviewflow.discussion.repository;

import com.reviewflow.shared.domain.Discussion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {

  @Query("SELECT d FROM Discussion d JOIN FETCH d.course LEFT JOIN FETCH d.assignment WHERE d.id = :id")
  Optional<Discussion> findWithCourseById(@Param("id") Long id);

  List<Discussion> findByCourseIdOrderByDueAtAsc(Long courseId);

  @Query(
      value =
          """
          SELECT
              d.id            AS discussionId,
              d.title         AS discussionTitle,
              d.due_at        AS dueAt,
              u.id            AS studentId,
              u.email         AS studentEmail,
              u.first_name    AS studentFirstName
          FROM discussions d
          JOIN course_enrollments ce ON ce.course_id = d.course_id
          JOIN users u ON ce.user_id = u.id
          WHERE d.is_published = true
            AND d.due_at BETWEEN :fromTs AND :toTs
            AND u.role = 'STUDENT'
            AND NOT EXISTS (
                SELECT 1 FROM discussion_posts dp
                WHERE dp.discussion_id = d.id
                  AND dp.author_id = u.id
                  AND dp.parent_post_id IS NULL
                  AND (d.is_graded = false OR dp.is_deleted = false)
            )
          """,
      nativeQuery = true)
  List<DiscussionReminderRow> findStudentsNeedingDiscussionReminder(
      @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs);

  @Query(
      value =
          """
          SELECT
              u.id AS userId,
              u.first_name AS firstName,
              u.last_name AS lastName,
              u.email AS email
          FROM course_enrollments ce
          JOIN users u ON u.id = ce.user_id
          JOIN discussions d ON d.id = :discussionId AND d.course_id = ce.course_id
          WHERE NOT EXISTS (
              SELECT 1 FROM discussion_posts dp
              WHERE dp.discussion_id = d.id
                AND dp.author_id = ce.user_id
                AND dp.parent_post_id IS NULL
                AND (d.is_graded = false OR dp.is_deleted = false)
          )
          ORDER BY u.last_name ASC, u.first_name ASC
          """,
      nativeQuery = true)
  List<NotPostedStudentRow> findNotPostedStudents(@Param("discussionId") Long discussionId);
}
