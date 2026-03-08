package com.reviewflow.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reviewflow.model.entity.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByCourse_Id(Long courseId);

    List<Assignment> findByCourse_IdAndIsPublishedTrue(Long courseId);

    @Query("SELECT a FROM Assignment a JOIN a.course c JOIN c.instructors i WHERE i.id = :instructorId")
    List<Assignment> findByCourseInstructorId(@Param("instructorId") Long instructorId);

    @Query("SELECT a FROM Assignment a JOIN a.course c JOIN c.enrollments e WHERE e.user.id = :userId")
    List<Assignment> findByCourseEnrollmentUserId(@Param("userId") Long userId);
    
    long countByIsPublishedTrue();
    
    @Query("SELECT a.id FROM Assignment a " +
           "WHERE a.isPublished = true " +
           "AND a.dueAt BETWEEN :start AND :end")
    List<Long> findPublishedDueBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end
    );
}
