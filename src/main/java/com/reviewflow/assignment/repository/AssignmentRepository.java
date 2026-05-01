package com.reviewflow.assignment.repository;

import com.reviewflow.shared.domain.Assignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

  @Query(
      "SELECT DISTINCT a FROM Assignment a JOIN FETCH a.course JOIN FETCH a.assignmentGroup LEFT"
          + " JOIN FETCH a.assignmentModule LEFT JOIN FETCH a.rubricCriteria WHERE a.id = :id")
  Optional<Assignment> findWithDetailsById(@Param("id") Long id);

  @Query(
      "SELECT a FROM Assignment a JOIN FETCH a.course JOIN FETCH a.assignmentGroup LEFT JOIN FETCH"
          + " a.assignmentModule WHERE a.course.id = :courseId")
  List<Assignment> findByCourseId(@Param("courseId") Long courseId);

  List<Assignment> findByAssignmentGroupId(Long groupId);

  List<Assignment> findByAssignmentGroupIdOrderByCreatedAtAsc(Long groupId);

  @Query(
      "SELECT a FROM Assignment a JOIN FETCH a.course JOIN FETCH a.assignmentGroup LEFT JOIN FETCH"
          + " a.assignmentModule WHERE a.course.id = :courseId AND a.isPublished = true")
  List<Assignment> findByCourseIdAndIsPublishedTrue(@Param("courseId") Long courseId);

  @Query(
      "SELECT a FROM Assignment a JOIN FETCH a.course JOIN FETCH a.assignmentGroup WHERE"
          + " a.course.id = :courseId AND a.assignmentModule IS NULL")
  List<Assignment> findByCourseIdAndAssignmentModuleIsNull(@Param("courseId") Long courseId);

  long countByAssignmentGroupId(Long groupId);

  @Query(
      "SELECT DISTINCT a FROM Assignment a JOIN FETCH a.course JOIN FETCH a.assignmentGroup LEFT"
          + " JOIN FETCH a.assignmentModule JOIN a.course c JOIN c.instructors i WHERE i.id ="
          + " :instructorId")
  List<Assignment> findByCourseInstructorId(@Param("instructorId") Long instructorId);

  @Query(
      "SELECT DISTINCT a FROM Assignment a JOIN FETCH a.course JOIN FETCH a.assignmentGroup LEFT"
          + " JOIN FETCH a.assignmentModule JOIN a.course c JOIN c.enrollments e WHERE e.user.id ="
          + " :userId")
  List<Assignment> findByCourseEnrollmentUserId(@Param("userId") Long userId);

  long countByIsPublishedTrue();

  @Query(
      "SELECT a.id FROM Assignment a "
          + "WHERE a.isPublished = true "
          + "AND a.dueAt BETWEEN :start AND :end")
  List<Long> findPublishedDueBetween(@Param("start") Instant start, @Param("end") Instant end);
}
