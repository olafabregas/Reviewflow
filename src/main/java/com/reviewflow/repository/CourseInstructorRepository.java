package com.reviewflow.repository;

import com.reviewflow.model.entity.CourseInstructor;
import com.reviewflow.model.entity.CourseInstructor.CourseInstructorId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, CourseInstructorId> {

    Optional<CourseInstructor> findByCourse_IdAndUser_Id(Long courseId, Long userId);

    boolean existsByCourse_IdAndUser_Id(Long courseId, Long userId);
}
