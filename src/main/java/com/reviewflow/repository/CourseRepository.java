package com.reviewflow.repository;

import com.reviewflow.model.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("SELECT c FROM Course c JOIN c.instructors i WHERE i.user.id = :userId")
    List<Course> findByInstructorId(@Param("userId") Long userId);

    @Query("SELECT c FROM Course c JOIN c.enrollments e WHERE e.user.id = :userId")
    List<Course> findByEnrolledUserId(@Param("userId") Long userId);

    long countByIsArchivedFalse();
}
