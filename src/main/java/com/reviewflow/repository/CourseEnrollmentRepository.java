package com.reviewflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reviewflow.model.entity.CourseEnrollment;
import com.reviewflow.model.entity.CourseEnrollment.CourseEnrollmentId;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, CourseEnrollmentId> {

    List<CourseEnrollment> findByCourse_Id(Long courseId);

    Optional<CourseEnrollment> findByCourse_IdAndUser_Id(Long courseId, Long userId);

    boolean existsByCourse_IdAndUser_Id(Long courseId, Long userId);

    void deleteByCourse_IdAndUser_Id(Long courseId, Long userId);

    long countByCourse_Id(Long courseId);

    long countByUser_Id(Long userId);

    @Query("""
        SELECT e.user.id FROM CourseEnrollment e
        JOIN Assignment a ON a.id = :assignmentId AND a.course.id = e.course.id
        WHERE e.user.id NOT IN (
            SELECT s.uploadedBy.id FROM Submission s
            WHERE s.assignment.id = :assignmentId
        )
        """)
    List<Long> findEnrolledStudentsWithoutSubmission(
            @Param("assignmentId") Long assignmentId
    );

    @Query("SELECT e.user.id FROM CourseEnrollment e WHERE e.course.id = :courseId")
    List<Long> findUserIdsByCourse_ID(@Param("courseId") Long courseId);

    @Query("""
            SELECT e
            FROM CourseEnrollment e
            JOIN FETCH e.user u
            WHERE e.course.id = :courseId
            ORDER BY u.lastName ASC, u.firstName ASC
            """)
    List<CourseEnrollment> findWithUserByCourseId(@Param("courseId") Long courseId);
}
