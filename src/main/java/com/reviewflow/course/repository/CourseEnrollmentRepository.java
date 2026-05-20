package com.reviewflow.course.repository;

import com.reviewflow.shared.domain.CourseEnrollment;
import com.reviewflow.shared.domain.CourseEnrollment.CourseEnrollmentId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentRepository
    extends JpaRepository<CourseEnrollment, CourseEnrollmentId> {

  List<CourseEnrollment> findByCourseId(Long courseId);

  Optional<CourseEnrollment> findByCourseIdAndUserId(Long courseId, Long userId);

  boolean existsByCourseIdAndUserId(Long courseId, Long userId);

  void deleteByCourseIdAndUserId(Long courseId, Long userId);

  long countByCourseId(Long courseId);

  long countByUserId(Long userId);

  @Query(
      """
      SELECT e.user.id FROM CourseEnrollment e
      JOIN Assignment a ON a.id = :assignmentId AND a.course.id = e.course.id
      WHERE e.user.id NOT IN (
          SELECT s.uploadedBy.id FROM Submission s
          WHERE s.assignment.id = :assignmentId
      )
      """)
  List<Long> findEnrolledStudentsWithoutSubmission(@Param("assignmentId") Long assignmentId);

  @Query("SELECT e.user.id FROM CourseEnrollment e WHERE e.course.id = :courseId")
  List<Long> findUserIdsByCourseId(@Param("courseId") Long courseId);

  @Query(
      """
      SELECT e
      FROM CourseEnrollment e
      JOIN FETCH e.user u
      WHERE e.course.id = :courseId
      ORDER BY u.lastName ASC, u.firstName ASC
      """)
  List<CourseEnrollment> findWithUserByCourseId(@Param("courseId") Long courseId);

  @Query(
      """
      SELECT e
      FROM CourseEnrollment e
      JOIN FETCH e.user u
      WHERE e.course.id = :courseId
      ORDER BY u.lastName ASC, u.firstName ASC
      """)
  Page<CourseEnrollment> findWithUserByCourseId(
      @Param("courseId") Long courseId, Pageable pageable);
}
